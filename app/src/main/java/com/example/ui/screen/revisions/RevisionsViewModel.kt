package com.example.ui.screen.revisions

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.R
import com.example.data.db.entity.RevisionDeckEntity
import com.example.data.db.entity.RevisionNoteEntity
import com.example.data.repository.GeminiOcrRepository
import com.example.data.repository.ImportMode
import com.example.data.repository.OcrEngineChoice
import com.example.data.repository.OcrEngineProvider
import com.example.data.repository.toSingleNote
import com.example.data.srs.FsrsScheduler
import com.example.data.srs.ReviewGrade
import com.example.data.srs.SrsSettings
import com.example.service.RevisionReminderWorker
import com.example.service.RevisionSyncWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** How a background card is being made. The UI turns this into localized text. */
enum class OcrTaskKind { ON_DEVICE, GEMINI, PHOTO }

/**
 * A card being created in the background from a photo. [errorMessage] is the technical cause
 * (shown as a detail under a localized headline), null while running.
 */
data class BackgroundOcrTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val deckId: String,
    val deckName: String,
    val kind: OcrTaskKind,
    val promptAddendum: String? = null,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val startTime: Long = System.currentTimeMillis()
)

data class RevisionsUiState(
    val decks: List<RevisionDeckEntity> = emptyList(),
    val dueNotes: List<RevisionNoteEntity> = emptyList(),
    val allNotes: List<RevisionNoteEntity> = emptyList(),
    val selectedDeckId: String = "default_deck",
    val dueCount: Int = 0,
    val totalCount: Int = 0,
    val isProcessingOcr: Boolean = false,
    val activeOcrTasks: List<BackgroundOcrTask> = emptyList(),
    val ocrError: String? = null,
    val srsSettings: SrsSettings = SrsSettings(),
    val lastExportSummary: String? = null,
    val hasApiKey: Boolean = false
)

/**
 * One review session. The queue is a snapshot taken at start, so grading a card (which moves
 * its due date) never reshuffles, shrinks or silently refills the queue.
 * In practice-ahead mode cards are shown but their schedule is left untouched.
 */
data class ReviewSessionState(
    val deckId: String = "",
    val isLoading: Boolean = true,
    val practiceAhead: Boolean = false,
    val queue: List<RevisionNoteEntity> = emptyList(),
    val index: Int = 0,
    val gradeCounts: Map<ReviewGrade, Int> = emptyMap(),
    val lastGraded: RevisionNoteEntity? = null,
    val lastGrade: ReviewGrade? = null
) {
    val current: RevisionNoteEntity? get() = queue.getOrNull(index)
    val isEmpty: Boolean get() = !isLoading && queue.isEmpty()
    val isFinished: Boolean get() = !isLoading && queue.isNotEmpty() && index >= queue.size
    val canUndo: Boolean get() = lastGraded != null && index > 0
}

class RevisionsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusFlowApplication.instance.revisionRepository
    private val localMediaStore = com.example.data.repository.LocalMediaStore(application)
    private val sharedPrefs = application.getSharedPreferences("focusflow_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(RevisionsUiState())
    val uiState: StateFlow<RevisionsUiState> = _uiState.asStateFlow()

    private val ocrEngineProvider = com.example.data.repository.OcrEngineProvider(
        apiKeyProvider = { getEffectiveApiKey() },
        // The list of models comes from Google (refreshed at most every 12 h); the choice in
        // Settings decides which is tried first, the others follow if it fails.
        modelOrderProvider = {
            val models = FocusFlowApplication.instance.geminiModels
            models.refresh(getEffectiveApiKey())
            val settings = _uiState.value.srsSettings
            models.attemptOrder(settings.selectedGeminiModel, settings.customGeminiModels)
        }
    )

    private val _reviewSession = MutableStateFlow(ReviewSessionState())
    val reviewSession: StateFlow<ReviewSessionState> = _reviewSession.asStateFlow()

    /** Starts (or keeps, if already running for this deck and mode) a review session. */
    fun startReviewSession(deckId: String, practiceAhead: Boolean = false) {
        val current = _reviewSession.value
        if (current.deckId == deckId && current.practiceAhead == practiceAhead &&
            !current.isLoading && !current.isEmpty && !current.isFinished
        ) return
        _reviewSession.value = ReviewSessionState(deckId = deckId, practiceAhead = practiceAhead, isLoading = true)
        viewModelScope.launch {
            val queue = repository.buildReviewQueue(
                deckId = deckId,
                includeNotDue = practiceAhead,
                limit = _uiState.value.srsSettings.maxReviewsPerDay
            )
            _reviewSession.update { it.copy(isLoading = false, queue = queue, index = 0) }
        }
    }

    fun gradeCurrentCard(grade: ReviewGrade) {
        val session = _reviewSession.value
        val card = session.current ?: return
        _reviewSession.update {
            it.copy(
                index = it.index + 1,
                gradeCounts = it.gradeCounts + (grade to (it.gradeCounts[grade] ?: 0) + 1),
                lastGrade = grade,
                lastGraded = card
            )
        }
        if (session.practiceAhead) return
        viewModelScope.launch {
            val latest = repository.getNoteById(card.id) ?: card // include edits made during the session
            _reviewSession.update { s -> if (s.lastGraded?.id == latest.id) s.copy(lastGraded = latest) else s }
            repository.upsertNote(FsrsScheduler.schedule(latest, grade))
            RevisionSyncWorker.scheduleSyncWork(getApplication())
        }
    }

    /** Puts the last graded card back on screen and restores its previous schedule. */
    fun undoLastGrade() {
        val session = _reviewSession.value
        val previous = session.lastGraded ?: return
        val grade = session.lastGrade
        _reviewSession.update {
            it.copy(
                index = (it.index - 1).coerceAtLeast(0),
                gradeCounts = if (grade == null) it.gradeCounts
                else it.gradeCounts + (grade to ((it.gradeCounts[grade] ?: 1) - 1).coerceAtLeast(0)),
                lastGraded = null,
                lastGrade = null
            )
        }
        if (!session.practiceAhead) viewModelScope.launch { repository.upsertNote(previous) }
    }

    /** The user's own key only (encrypted on device). No key is ever bundled in the APK. */
    private val secretStore = com.example.data.security.SecretStore(application)

    fun getEffectiveApiKey(): String =
        _uiState.value.srsSettings.geminiApiKey.trim().takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }.orEmpty()

    init {
        loadSettings()
        observeData()
    }

    private fun loadSettings() {
        val defaultModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite"
        )
        val savedModelsCsv = sharedPrefs.getString("gemini_available_models", null)
        val availableModels = if (savedModelsCsv.isNullOrBlank()) {
            defaultModels
        } else {
            savedModelsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        }
        // The old default (a fixed model) becomes "newest Flash" once; a model the user picked stays.
        val storedModel = sharedPrefs.getString("selected_gemini_model", null)
        val selectedModel = if (storedModel == null || (storedModel == "gemini-2.5-flash" && !sharedPrefs.getBoolean("gemini_auto_migrated", false))) {
            sharedPrefs.edit().putBoolean("gemini_auto_migrated", true).apply()
            com.example.data.repository.GeminiModels.AUTO_FLASH
        } else storedModel

        val settings = SrsSettings(
            newCardsPerDay = sharedPrefs.getInt("srs_new_cards_per_day", 20),
            maxReviewsPerDay = sharedPrefs.getInt("srs_max_reviews_per_day", 200),
            startingEaseFactor = sharedPrefs.getFloat("srs_starting_ease", 2.5f),
            reminderHour = sharedPrefs.getInt("srs_reminder_hour", 19),
            reminderMinute = sharedPrefs.getInt("srs_reminder_minute", 0),
            notificationsEnabled = sharedPrefs.getBoolean("srs_notifications_enabled", true),
            geminiApiKey = secretStore.geminiApiKey,
            explainModeEnabled = sharedPrefs.getBoolean("srs_explain_mode_enabled", false),
            customPromptOverride = sharedPrefs.getString("srs_custom_prompt_override", null),
            selectedGeminiModel = selectedModel,
            availableGeminiModels = availableModels
        )
        val lastSummary = sharedPrefs.getString("srs_last_export_summary", null)
        _uiState.update { it.copy(srsSettings = settings, lastExportSummary = lastSummary, hasApiKey = getEffectiveApiKey().isNotBlank()) }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                repository.allDecks,
                repository.dueNotes,
                repository.allNotes
            ) { decks, due, all ->
                Triple(decks, due, all)
            }.collect { (decks, due, all) ->
                _uiState.update { state ->
                    val selectedDeckExists = decks.any { it.id == state.selectedDeckId }
                    val activeDeckId = if (selectedDeckExists) state.selectedDeckId else (decks.firstOrNull()?.id ?: "default_deck")
                    state.copy(
                        decks = decks,
                        dueNotes = due,
                        allNotes = all,
                        selectedDeckId = activeDeckId,
                        dueCount = due.size,
                        totalCount = all.size,
                        hasApiKey = getEffectiveApiKey().isNotBlank()
                    )
                }
            }
        }
    }

    fun setSelectedDeck(deckId: String) {
        _uiState.update { it.copy(selectedDeckId = deckId) }
    }

    fun addDeck(name: String, colorHex: String = "#4CAF50") {
        addDeckAndSelect(name, colorHex)
    }

    fun addDeckAndSelect(name: String, colorHex: String = "#4CAF50", onCreated: (String) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val deck = RevisionDeckEntity(name = name, colorHex = colorHex)
            repository.upsertDeck(deck)
            setSelectedDeck(deck.id)
            onCreated(deck.id)
        }
    }

    fun generateTitleFromContent(
        content: String,
        onResult: (String) -> Unit
    ) {
        val promptText = content.ifBlank { "Study Card" }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOcr = true) }
            try {
                val apiKey = getEffectiveApiKey()
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _uiState.update { it.copy(isProcessingOcr = false) }
                    val fallback = promptText.trim().lines().firstOrNull()?.take(35) ?: "Study Card"
                    onResult(fallback)
                    return@launch
                }
                val titleEngine = com.example.data.repository.GeminiTitleEngine { apiKey }
                val titleRes = titleEngine.generateTitle(promptText.toByteArray(Charsets.UTF_8), "text/plain")
                _uiState.update { it.copy(isProcessingOcr = false) }
                onResult(titleRes.title)
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessingOcr = false) }
                val fallback = promptText.trim().lines().firstOrNull()?.take(35) ?: "Study Card"
                onResult(fallback)
            }
        }
    }

    fun generateTitleFromAudio(
        audioFile: java.io.File?,
        currentTitleText: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOcr = true) }
            try {
                val apiKey = getEffectiveApiKey()
                if (audioFile != null && audioFile.exists() && audioFile.length() > 0L && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                    val audioBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { audioFile.readBytes() }
                    val titleEngine = com.example.data.repository.GeminiTitleEngine { apiKey }
                    val titleRes = titleEngine.generateTitle(
                        audioBytes,
                        com.example.data.repository.LocalMediaStore.mimeTypeFor(audioFile.absolutePath)
                    )
                    _uiState.update { it.copy(isProcessingOcr = false) }
                    onResult(titleRes.title)
                    return@launch
                }
                if (currentTitleText.isNotBlank() && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                    val titleEngine = com.example.data.repository.GeminiTitleEngine { apiKey }
                    val titleRes = titleEngine.generateTitle(currentTitleText.toByteArray(Charsets.UTF_8), "text/plain")
                    _uiState.update { it.copy(isProcessingOcr = false) }
                    onResult(titleRes.title)
                    return@launch
                }
                _uiState.update { it.copy(isProcessingOcr = false) }
                val fallback = currentTitleText.ifBlank { "Voice Note ${System.currentTimeMillis() % 10000}" }
                onResult(fallback)
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessingOcr = false) }
                val fallback = currentTitleText.ifBlank { "Voice Note ${System.currentTimeMillis() % 10000}" }
                onResult(fallback)
            }
        }
    }

    /** Renames and/or recolours a deck; its cards are untouched. */
    fun updateDeck(deckId: String, name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val deck = repository.getDeckById(deckId) ?: return@launch
            repository.upsertDeck(deck.copy(name = name.trim(), colorHex = colorHex))
        }
    }

    /** Deleting a deck always deletes its cards and their media (no orphaned cards). */
    fun deleteDeck(deck: RevisionDeckEntity) = deleteDeckAndNotes(deck.id)

    suspend fun ensureAtLeastOneDeck(): String {
        val decks = repository.getAllDecksOnce()
        return decks.firstOrNull()?.id ?: repository.ensureDefaultDeck()
    }

    fun deleteDeckAndNotes(deckId: String) {
        viewModelScope.launch {
            val mediaPaths = repository.deleteDeckAndNotes(deckId)
            localMediaStore.deleteAll(mediaPaths)
        }
    }

    fun moveNoteToDeck(noteId: String, newDeckId: String) {
        viewModelScope.launch {
            repository.getNoteById(noteId)?.let { note ->
                repository.upsertNote(note.copy(deckId = newDeckId, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun updateNote(note: RevisionNoteEntity) {
        viewModelScope.launch {
            repository.upsertNote(note.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun submitReviewGrade(note: RevisionNoteEntity, grade: ReviewGrade) {
        viewModelScope.launch {
            val updatedNote = FsrsScheduler.schedule(note, grade)
            repository.upsertNote(updatedNote)
            RevisionSyncWorker.scheduleSyncWork(getApplication())
        }
    }

    fun processCapturedImage(
        croppedBitmap: Bitmap,
        explainMode: Boolean = false,
        engineChoice: OcrEngineChoice = if (hasApiKey()) OcrEngineChoice.GEMINI else OcrEngineChoice.ML_KIT,
        onComplete: (Boolean) -> Unit
    ) {
        processCapturedImageWithCustomPrompt(
            croppedBitmap = croppedBitmap,
            temporaryPromptAddendum = null,
            explainMode = explainMode,
            targetDeckId = _uiState.value.selectedDeckId,
            engineChoice = engineChoice,
            onResult = { noteResult, error ->
                if (noteResult != null) {
                    val singleNote = noteResult.toSingleNote(_uiState.value.selectedDeckId)
                    viewModelScope.launch {
                        repository.upsertNote(singleNote)
                        RevisionSyncWorker.scheduleSyncWork(getApplication())
                    }
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
        )
    }

    fun hasApiKey(): Boolean = getEffectiveApiKey().isNotBlank()

    /** Title for a photo card when none was typed or detected (stored once, in the current language). */
    private fun defaultPhotoTitle(): String = getApplication<Application>().getString(R.string.study_card_type_photo)

    fun launchBackgroundOcrCardCreation(
        croppedBitmap: Bitmap,
        temporaryPromptAddendum: String?,
        explainMode: Boolean = false,
        targetDeckId: String,
        engineChoice: OcrEngineChoice = OcrEngineChoice.ML_KIT
    ) {
        val taskId = java.util.UUID.randomUUID().toString()
        val deckName = _uiState.value.decks.find { it.id == targetDeckId }?.name.orEmpty()

        val task = BackgroundOcrTask(
            id = taskId,
            deckId = targetDeckId,
            deckName = deckName,
            kind = if (engineChoice == OcrEngineChoice.GEMINI) OcrTaskKind.GEMINI else OcrTaskKind.ON_DEVICE,
            promptAddendum = temporaryPromptAddendum
        )

        _uiState.update { state ->
            state.copy(
                activeOcrTasks = state.activeOcrTasks + task,
                isProcessingOcr = true
            )
        }

        viewModelScope.launch {
            try {
                val mode = if (explainMode) com.example.data.repository.ExtractionMode.EXPLAIN else com.example.data.repository.ExtractionMode.VERBATIM
                val engine = ocrEngineProvider.get(engineChoice)
                val result = engine.extractStructuredContent(
                    bitmap = croppedBitmap,
                    mode = mode,
                    promptOverride = _uiState.value.srsSettings.customPromptOverride,
                    temporaryPromptAddendum = temporaryPromptAddendum
                )

                val singleNote = result.toSingleNote(targetDeckId)
                repository.upsertNote(singleNote)
                RevisionSyncWorker.scheduleSyncWork(getApplication())

                _uiState.update { state ->
                    val remaining = state.activeOcrTasks.filterNot { it.id == taskId }
                    state.copy(
                        activeOcrTasks = remaining,
                        isProcessingOcr = remaining.isNotEmpty()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("RevisionsViewModel", "Background OCR failed with $engineChoice", e)
                val errMsg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.message?.takeIf { it.isNotBlank() }
                _uiState.update { state ->
                    val updated = state.activeOcrTasks.map {
                        if (it.id == taskId) it.copy(isError = true, errorMessage = errMsg)
                        else it
                    }
                    state.copy(
                        activeOcrTasks = updated,
                        isProcessingOcr = updated.any { !it.isError },
                        ocrError = errMsg
                    )
                }
            }
        }
    }

    fun launchBackgroundLocalImageCardCreation(
        bitmap: Bitmap,
        userTitle: String?,
        deckId: String
    ) {
        val taskId = java.util.UUID.randomUUID().toString()
        val deckName = _uiState.value.decks.find { it.id == deckId }?.name.orEmpty()
        val task = BackgroundOcrTask(
            id = taskId,
            deckId = deckId,
            deckName = deckName,
            kind = OcrTaskKind.PHOTO,
            promptAddendum = userTitle
        )

        _uiState.update { state ->
            state.copy(activeOcrTasks = state.activeOcrTasks + task, isProcessingOcr = true)
        }

        viewModelScope.launch {
            try {
                val savedPath = localMediaStore.savePhoto(bitmap)
                var titleToUse = userTitle?.trim().orEmpty()

                if (titleToUse.isBlank()) {
                    try {
                        if (hasApiKey()) {
                            val bos = java.io.ByteArrayOutputStream()
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                            }
                            val titleEngine = com.example.data.repository.GeminiTitleEngine { getEffectiveApiKey() }
                            val res = titleEngine.generateTitle(bos.toByteArray(), "image/jpeg")
                            titleToUse = res.title
                        } else {
                            val mlKit = ocrEngineProvider.get(OcrEngineChoice.ML_KIT)
                            val res = mlKit.extractStructuredContent(bitmap, com.example.data.repository.ExtractionMode.VERBATIM)
                            titleToUse = res.title
                        }
                    } catch (e: Exception) {
                        titleToUse = defaultPhotoTitle()
                    }
                }

                val note = RevisionNoteEntity(
                    deckId = deckId,
                    title = titleToUse.ifBlank { defaultPhotoTitle() },
                    contentBlocksJson = "",
                    plainTextPreview = "Photo Card",
                    contentMarkdown = "",
                    mediaType = "IMAGE",
                    mediaFilePath = savedPath
                )
                repository.upsertNote(note)
                _uiState.update { state ->
                    val remaining = state.activeOcrTasks.filterNot { it.id == taskId }
                    state.copy(activeOcrTasks = remaining, isProcessingOcr = remaining.isNotEmpty())
                }
            } catch (e: Exception) {
                val errMsg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: e.message?.takeIf { it.isNotBlank() }
                _uiState.update { state ->
                    val updated = state.activeOcrTasks.map {
                        if (it.id == taskId) it.copy(isError = true, errorMessage = errMsg)
                        else it
                    }
                    state.copy(activeOcrTasks = updated, isProcessingOcr = updated.any { !it.isError })
                }
            }
        }
    }

    fun dismissOcrTask(taskId: String) {
        _uiState.update { state ->
            val remaining = state.activeOcrTasks.filterNot { it.id == taskId }
            state.copy(activeOcrTasks = remaining, isProcessingOcr = remaining.isNotEmpty())
        }
    }

    fun processCapturedImageWithCustomPrompt(
        croppedBitmap: Bitmap,
        temporaryPromptAddendum: String?,
        explainMode: Boolean = false,
        targetDeckId: String,
        engineChoice: OcrEngineChoice = OcrEngineChoice.ML_KIT,
        onResult: (com.example.data.repository.GeminiNoteResult?, String?) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOcr = true, ocrError = null) }
            try {
                val mode = if (explainMode) com.example.data.repository.ExtractionMode.EXPLAIN else com.example.data.repository.ExtractionMode.VERBATIM
                val engine = ocrEngineProvider.get(engineChoice)
                val result = engine.extractStructuredContent(
                    bitmap = croppedBitmap,
                    mode = mode,
                    promptOverride = _uiState.value.srsSettings.customPromptOverride,
                    temporaryPromptAddendum = temporaryPromptAddendum
                )

                _uiState.update { it.copy(isProcessingOcr = false) }
                onResult(result, null)
            } catch (e: Exception) {
                android.util.Log.e("RevisionsViewModel", "OCR process failed with $engineChoice", e)
                val errorMessage = when {
                    !e.message.isNullOrBlank() -> e.message!!
                    !e.localizedMessage.isNullOrBlank() -> e.localizedMessage!!
                    else -> e.javaClass.simpleName
                }
                _uiState.update { it.copy(isProcessingOcr = false, ocrError = errorMessage) }
                onResult(null, errorMessage)
            }
        }
    }

    fun createManualNote(
        title: String,
        answerText: String,
        deckId: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val blocksJson = org.json.JSONArray().apply {
                put(org.json.JSONObject().apply {
                    put("type", "text")
                    put("content", answerText)
                    put("highlights", org.json.JSONArray())
                })
            }.toString()

            val note = RevisionNoteEntity(
                deckId = deckId,
                title = title.ifBlank { "Manual Card" },
                contentBlocksJson = blocksJson,
                plainTextPreview = answerText,
                contentMarkdown = answerText
            )
            repository.upsertNote(note)
            RevisionSyncWorker.scheduleSyncWork(getApplication())
            onComplete()
        }
    }

    fun createLocalImageCard(
        bitmap: Bitmap,
        userTitle: String?,
        deckId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOcr = true, ocrError = null) }
            try {
                val savedPath = localMediaStore.savePhoto(bitmap)
                var titleToUse = userTitle?.trim().orEmpty()

                if (titleToUse.isBlank()) {
                    try {
                        if (hasApiKey()) {
                            val bos = java.io.ByteArrayOutputStream()
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                            }
                            val titleEngine = com.example.data.repository.GeminiTitleEngine { getEffectiveApiKey() }
                            val res = titleEngine.generateTitle(bos.toByteArray(), "image/jpeg")
                            titleToUse = res.title
                        } else {
                            val mlKit = ocrEngineProvider.get(OcrEngineChoice.ML_KIT)
                            val res = mlKit.extractStructuredContent(bitmap, com.example.data.repository.ExtractionMode.VERBATIM)
                            titleToUse = res.title
                        }
                    } catch (e: Exception) {
                        titleToUse = defaultPhotoTitle()
                    }
                }

                val note = RevisionNoteEntity(
                    deckId = deckId,
                    title = titleToUse.ifBlank { defaultPhotoTitle() },
                    contentBlocksJson = "",
                    plainTextPreview = "Photo Card",
                    contentMarkdown = "",
                    mediaType = "IMAGE",
                    mediaFilePath = savedPath
                )
                repository.upsertNote(note)
                _uiState.update { it.copy(isProcessingOcr = false) }
                onComplete(true, null)
            } catch (e: Exception) {
                android.util.Log.e("RevisionsViewModel", "Local photo card creation failed", e)
                _uiState.update { it.copy(isProcessingOcr = false, ocrError = e.message) }
                onComplete(false, e.message)
            }
        }
    }

    fun createLocalAudioCard(
        recordingFile: java.io.File,
        userTitle: String?,
        deckId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingOcr = true, ocrError = null) }
            try {
                val savedPath = localMediaStore.saveAudioFrom(recordingFile)
                var titleToUse = userTitle?.trim().orEmpty()

                if (titleToUse.isBlank()) {
                    try {
                        if (!hasApiKey()) throw IllegalStateException("No Gemini key")
                        val audioBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { java.io.File(savedPath).readBytes() }
                        val titleEngine = com.example.data.repository.GeminiTitleEngine { getEffectiveApiKey() }
                        val res = titleEngine.generateTitle(
                            audioBytes,
                            com.example.data.repository.LocalMediaStore.mimeTypeFor(savedPath)
                        )
                        titleToUse = res.title
                    } catch (e: Exception) {
                        titleToUse = "Voice Note Card"
                    }
                }

                val note = RevisionNoteEntity(
                    deckId = deckId,
                    title = titleToUse.ifBlank { "Voice Note Card" },
                    contentBlocksJson = "",
                    plainTextPreview = "Voice Note Card",
                    contentMarkdown = "",
                    mediaType = "AUDIO",
                    mediaFilePath = savedPath
                )
                repository.upsertNote(note)
                _uiState.update { it.copy(isProcessingOcr = false) }
                onComplete(true, null)
            } catch (e: Exception) {
                android.util.Log.e("RevisionsViewModel", "Local audio card creation failed", e)
                _uiState.update { it.copy(isProcessingOcr = false, ocrError = e.message) }
                onComplete(false, e.message)
            }
        }
    }

    fun updateSrsSettings(settings: SrsSettings) {
        secretStore.geminiApiKey = settings.geminiApiKey
        sharedPrefs.edit()
            .putInt("srs_new_cards_per_day", settings.newCardsPerDay)
            .putInt("srs_max_reviews_per_day", settings.maxReviewsPerDay)
            .putFloat("srs_starting_ease", settings.startingEaseFactor)
            .putInt("srs_reminder_hour", settings.reminderHour)
            .putInt("srs_reminder_minute", settings.reminderMinute)
            .putBoolean("srs_notifications_enabled", settings.notificationsEnabled)
            .putBoolean("srs_explain_mode_enabled", settings.explainModeEnabled)
            .putString("srs_custom_prompt_override", settings.customPromptOverride)
            .putString("selected_gemini_model", settings.selectedGeminiModel)
            .putString("gemini_available_models", settings.availableGeminiModels.joinToString(","))
            .apply()

        _uiState.update { it.copy(srsSettings = settings, hasApiKey = getEffectiveApiKey().isNotBlank()) }
        RevisionReminderWorker.scheduleRevisionReminder(getApplication(), settings)
    }

    fun setGeminiApiKey(apiKey: String) {
        val current = _uiState.value.srsSettings
        updateSrsSettings(current.copy(geminiApiKey = apiKey))
    }

    fun setSelectedGeminiModel(model: String) {
        val current = _uiState.value.srsSettings
        updateSrsSettings(current.copy(selectedGeminiModel = model))
    }

    fun addCustomGeminiModel(model: String) {
        val current = _uiState.value.srsSettings
        val cleanModel = model.trim().lowercase()
        if (cleanModel.isNotBlank() && !current.availableGeminiModels.contains(cleanModel)) {
            val updated = current.availableGeminiModels + cleanModel
            updateSrsSettings(current.copy(availableGeminiModels = updated, selectedGeminiModel = cleanModel))
        }
    }

    fun removeCustomGeminiModel(model: String) {
        val current = _uiState.value.srsSettings
        if (current.availableGeminiModels.size > 1) {
            val updated = current.availableGeminiModels.filter { it != model }
            val newSelected = if (current.selectedGeminiModel == model) updated.first() else current.selectedGeminiModel
            updateSrsSettings(current.copy(availableGeminiModels = updated, selectedGeminiModel = newSelected))
        }
    }

    fun deleteNote(note: RevisionNoteEntity) {
        viewModelScope.launch {
            if (!note.mediaFilePath.isNullOrBlank()) {
                localMediaStore.delete(note.mediaFilePath)
            }
            repository.deleteNote(note)
            // Also remove the cloud copy (queued until there is a connection).
            note.firestoreId?.let { RevisionSyncWorker.scheduleRemoteDelete(getApplication(), listOf(it)) }
        }
    }
}
