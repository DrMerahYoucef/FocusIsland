package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.data.db.DatabaseSeeder
import com.example.data.db.entity.RevisionDeckEntity
import com.example.data.db.entity.RevisionNoteEntity
import com.example.data.db.entity.SyncStatus
import com.example.data.repository.ImportMode
import com.example.data.repository.LocalMediaStore
import com.example.data.repository.RevisionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class CardTypeFilter(val label: String) {
    ALL("All Cards (Text, Image, Audio)"),
    TEXT("Text & Markdown Cards Only"),
    IMAGE("Photo & Image Cards Only"),
    AUDIO("Voice & Audio Cards Only")
}

data class BackupRestoreResult(
    val notesCount: Int,
    val decksCount: Int,
    val mediaCount: Int,
    val summaryText: String
)

/**
 * .focuscards = ZIP containing backup_manifest.json (decks + cards + review history)
 * and media/<file> for photo and voice cards. Format version 2 (unchanged).
 */
object CardBackupEngine {

    private const val TAG = "CardBackupEngine"
    private const val MANIFEST_FILE_NAME = "backup_manifest.json"
    private const val MEDIA_PREFIX = "media/"
    private const val FORMAT_VERSION = 2
    private const val MAX_ENTRIES = 20_000
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB uncompressed (zip-bomb guard)

    suspend fun createBackup(
        context: Context,
        repository: RevisionRepository,
        cardTypeFilter: CardTypeFilter,
        deckIdFilter: String, // RevisionRepository.ALL_DECKS_ID or a deck id
        outputFile: File,
        onProgress: (progress: Float, status: String) -> Unit
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val res = context.resources
        onProgress(0.05f, context.getString(R.string.backup_progress_gathering))

        val allDecks = repository.getAllDecksOnce()
        val allNotes = repository.getAllNotesOnce()
        val allDecksSelected = deckIdFilter == RevisionRepository.ALL_DECKS_ID

        val notes = allNotes.filter { note ->
            val matchesDeck = allDecksSelected || note.deckId == deckIdFilter
            matchesDeck && note.matches(cardTypeFilter)
        }
        val decks = when {
            allDecksSelected && cardTypeFilter == CardTypeFilter.ALL -> allDecks
            allDecksSelected -> notes.mapTo(HashSet()) { it.deckId }.let { ids -> allDecks.filter { it.id in ids } }
            else -> allDecks.filter { it.id == deckIdFilter }
        }
        if (notes.isEmpty() && decks.isEmpty()) {
            throw IllegalStateException(context.getString(R.string.backup_error_no_match))
        }
        onProgress(0.15f, res.getQuantityString(R.plurals.backup_progress_found, notes.size, notes.size))

        val media = notes.mapNotNull { note ->
            note.mediaFilePath?.let(::File)?.takeIf { it.isFile }
        }.distinctBy { it.name }

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
            onProgress(0.20f, context.getString(R.string.backup_progress_manifest))
            zos.putNextEntry(ZipEntry(MANIFEST_FILE_NAME))
            zos.write(buildManifest(decks, notes, cardTypeFilter, deckIdFilter).toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            media.forEachIndexed { index, file ->
                onProgress(
                    0.25f + 0.70f * (index.toFloat() / media.size.coerceAtLeast(1)),
                    context.getString(R.string.backup_progress_media, index + 1, media.size)
                )
                zos.putNextEntry(ZipEntry(MEDIA_PREFIX + file.name))
                file.inputStream().use { it.copyTo(zos, BUFFER) }
                zos.closeEntry()
            }
        }

        onProgress(1f, context.getString(R.string.backup_progress_done))
        BackupRestoreResult(
            notesCount = notes.size,
            decksCount = decks.size,
            mediaCount = media.size,
            summaryText = summary(context, notes.size, decks.size, media.size)
        )
    }

    suspend fun restoreBackup(
        context: Context,
        repository: RevisionRepository,
        inputFileUri: Uri,
        importMode: ImportMode,
        onProgress: (progress: Float, status: String) -> Unit
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val res = context.resources
        onProgress(0.05f, context.getString(R.string.restore_progress_reading))

        val tempDir = File(context.cacheDir, "backup_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempMediaDir = File(tempDir, "media").apply { mkdirs() }
        try {
            // 1. Unpack into a private temp folder. Nothing in the app changes until the backup is fully read.
            val input: InputStream = context.contentResolver.openInputStream(inputFileUri)
                ?: throw IllegalArgumentException(context.getString(R.string.restore_error_open))
            var manifestFile: File? = null
            var entries = 0
            var totalBytes = 0L
            ZipInputStream(BufferedInputStream(input)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (++entries > MAX_ENTRIES) throw IllegalStateException(context.getString(R.string.restore_error_too_large))
                    if (!entry.isDirectory) {
                        // Only the base name is ever used, so "../" entries can't escape the temp folder.
                        val target = when {
                            entry.name == MANIFEST_FILE_NAME -> File(tempDir, MANIFEST_FILE_NAME).also { manifestFile = it }
                            entry.name.startsWith(MEDIA_PREFIX) -> File(tempMediaDir, File(entry.name).name)
                            else -> null
                        }
                        if (target != null) {
                            totalBytes += FileOutputStream(target).use { out -> zis.copyCapped(out, MAX_TOTAL_BYTES - totalBytes, context) }
                        }
                    }
                    onProgress(
                        (0.10f + entries * 0.02f).coerceAtMost(0.45f),
                        context.getString(R.string.restore_progress_extracting, entries)
                    )
                    entry = zis.nextEntry
                }
            }
            val manifest = manifestFile?.takeIf { it.exists() }
                ?: throw IllegalStateException(context.getString(R.string.restore_error_invalid))

            // 2. Parse.
            onProgress(0.50f, context.getString(R.string.restore_progress_parsing))
            val mediaDir = LocalMediaStore.mediaDir(context)
            val root = JSONObject(manifest.readText(Charsets.UTF_8))
            val decks = parseDecks(root.optJSONArray("decks") ?: JSONArray())
            var notes = parseNotes(root.optJSONArray("notes") ?: JSONArray(), mediaDir)

            // Cards whose deck isn't in the backup (or on the device, when merging) go to the default deck.
            val knownDeckIds = decks.mapTo(HashSet()) { it.id }.apply {
                add(DatabaseSeeder.DEFAULT_DECK_ID)
                if (importMode == ImportMode.MERGE) repository.getAllDecksOnce().mapTo(this) { it.id }
            }
            notes = notes.map { if (it.deckId in knownDeckIds) it else it.copy(deckId = DatabaseSeeder.DEFAULT_DECK_ID) }

            // 3. Media first: copy (overwrite same-named files, which hold the same content).
            onProgress(0.65f, context.getString(R.string.restore_progress_media))
            val restoredMedia = tempMediaDir.listFiles().orEmpty().filter { it.isFile }
            restoredMedia.forEach { it.copyTo(File(mediaDir, it.name), overwrite = true) }

            // 4. Database.
            onProgress(0.80f, res.getQuantityString(R.plurals.restore_progress_writing, notes.size, notes.size))
            when (importMode) {
                ImportMode.REPLACE_ALL -> {
                    val oldMedia = repository.getAllMediaPaths()
                    repository.replaceAll(decks, notes)
                    // Delete only files no restored card points to, AFTER the new ones are in place.
                    val keep = notes.mapNotNullTo(HashSet()) { it.mediaFilePath?.let { p -> File(p).canonicalPath } }
                    LocalMediaStore(context).deleteAll(oldMedia.filter { File(it).canonicalPath !in keep })
                }
                ImportMode.MERGE -> {
                    decks.forEach { repository.upsertDeck(it) }
                    repository.ensureDefaultDeck()
                    val toWrite = notes.filter { note ->
                        val existing = repository.getNoteById(note.id)
                        existing == null || (note.lastReviewedAt ?: 0L) >= (existing.lastReviewedAt ?: 0L)
                    }
                    repository.upsertNotes(toWrite)
                }
            }

            onProgress(1f, context.getString(R.string.restore_progress_done))
            BackupRestoreResult(
                notesCount = notes.size,
                decksCount = decks.size,
                mediaCount = restoredMedia.size,
                summaryText = summary(context, notes.size, decks.size, restoredMedia.size)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            throw e
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- helpers

    private const val BUFFER = 32 * 1024

    private fun RevisionNoteEntity.matches(filter: CardTypeFilter): Boolean = when (filter) {
        CardTypeFilter.ALL -> true
        CardTypeFilter.TEXT -> mediaType == null && mediaFilePath.isNullOrBlank()
        CardTypeFilter.IMAGE -> mediaType == "IMAGE" || hasExtension("jpg", "jpeg", "png", "webp")
        CardTypeFilter.AUDIO -> mediaType == "AUDIO" || hasExtension("m4a", "mp3", "wav", "3gp", "aac")
    }

    private fun RevisionNoteEntity.hasExtension(vararg ext: String): Boolean =
        mediaFilePath?.let { File(it).extension.lowercase() in ext } == true

    /** Copies at most [remaining] bytes; throws if the entry is larger (zip-bomb guard). */
    private fun InputStream.copyCapped(out: OutputStream, remaining: Long, context: Context): Long {
        val buffer = ByteArray(BUFFER)
        var copied = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            copied += read
            if (copied > remaining) throw IllegalStateException(context.getString(R.string.restore_error_too_large))
            out.write(buffer, 0, read)
        }
        return copied
    }

    private fun buildManifest(
        decks: List<RevisionDeckEntity>,
        notes: List<RevisionNoteEntity>,
        filter: CardTypeFilter,
        deckIdFilter: String
    ): JSONObject {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return JSONObject().apply {
            put("appName", "Focus Island")
            put("version", FORMAT_VERSION)
            put("exportedAt", iso.format(Date()))
            put("exportedAtMs", System.currentTimeMillis())
            put("cardTypeFilter", filter.name)
            put("deckIdFilter", deckIdFilter)
            put("decks", JSONArray().apply {
                decks.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("name", d.name)
                        put("colorHex", d.colorHex)
                        put("createdAt", d.createdAt)
                    })
                }
            })
            put("notes", JSONArray().apply {
                notes.forEach { n ->
                    put(JSONObject().apply {
                        put("id", n.id)
                        put("deckId", n.deckId)
                        put("title", n.title)
                        put("contentBlocksJson", n.contentBlocksJson)
                        put("plainTextPreview", n.plainTextPreview)
                        put("contentMarkdown", n.contentMarkdown)
                        put("mediaType", n.mediaType ?: JSONObject.NULL)
                        put("mediaFileName", n.mediaFilePath?.let(::File)?.takeIf { it.isFile }?.name ?: JSONObject.NULL)
                        put("stability", n.stability.toDouble())
                        put("difficulty", n.difficulty.toDouble())
                        put("retrievability", n.retrievability.toDouble())
                        put("state", n.state)
                        put("intervalDays", n.intervalDays)
                        put("repetitions", n.repetitions)
                        put("dueDate", n.dueDate)
                        put("lastReviewedAt", n.lastReviewedAt ?: JSONObject.NULL)
                        put("syncStatus", n.syncStatus.name)
                    })
                }
            })
        }
    }

    private fun parseDecks(array: JSONArray): List<RevisionDeckEntity> =
        (0 until array.length()).mapNotNull { i ->
            val d = array.optJSONObject(i) ?: return@mapNotNull null
            val id = d.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            RevisionDeckEntity(
                id = id,
                name = d.optString("name").ifBlank { DatabaseSeeder.DEFAULT_DECK_NAME },
                colorHex = d.optString("colorHex", DatabaseSeeder.DEFAULT_DECK_COLOR),
                createdAt = d.optLong("createdAt", System.currentTimeMillis())
            )
        }

    private fun parseNotes(array: JSONArray, mediaDir: File): List<RevisionNoteEntity> {
        val nowMs = System.currentTimeMillis()
        return (0 until array.length()).mapNotNull { i ->
            val n = array.optJSONObject(i) ?: return@mapNotNull null
            val id = n.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mediaFileName = if (n.isNull("mediaFileName")) null else File(n.optString("mediaFileName")).name
            val intervalDays = n.optInt("intervalDays", 0)
            val repetitions = n.optInt("repetitions", 0)
            val lastReviewedAt = if (n.isNull("lastReviewedAt")) null else n.optLong("lastReviewedAt")

            val fsrsState = if (n.has("stability") && n.has("difficulty")) {
                com.example.data.srs.FsrsMemoryState(
                    stability = n.optDouble("stability", 0.0).toFloat(),
                    difficulty = n.optDouble("difficulty", 0.0).toFloat(),
                    retrievability = n.optDouble("retrievability", 1.0).toFloat(),
                    state = n.optString("state", "NEW").ifBlank { "NEW" },
                    intervalDays = intervalDays,
                    repetitions = repetitions
                )
            } else {
                com.example.data.srs.FsrsScheduler.migrateSm2ToFsrs(
                    easeFactor = n.optDouble("easeFactor", 2.5).toFloat(),
                    intervalDays = intervalDays,
                    repetitions = repetitions,
                    lastReviewedAt = lastReviewedAt,
                    nowMs = nowMs
                )
            }

            RevisionNoteEntity(
                id = id,
                deckId = n.optString("deckId").ifBlank { DatabaseSeeder.DEFAULT_DECK_ID },
                title = n.optString("title").ifBlank { com.example.FocusFlowApplication.instance.getString(R.string.study_untitled) },
                contentBlocksJson = n.optString("contentBlocksJson", ""),
                plainTextPreview = n.optString("plainTextPreview", ""),
                contentMarkdown = n.optString("contentMarkdown", ""),
                mediaType = if (n.isNull("mediaType")) null else n.optString("mediaType"),
                mediaFilePath = mediaFileName?.takeIf { it.isNotBlank() }?.let { File(mediaDir, it).absolutePath },
                stability = fsrsState.stability,
                difficulty = fsrsState.difficulty,
                retrievability = fsrsState.retrievability,
                state = fsrsState.state,
                intervalDays = fsrsState.intervalDays,
                repetitions = fsrsState.repetitions,
                dueDate = n.optLong("dueDate", nowMs),
                lastReviewedAt = lastReviewedAt,
                syncStatus = runCatching { SyncStatus.valueOf(n.optString("syncStatus")) }.getOrDefault(SyncStatus.PENDING_UPLOAD)
            )
        }
    }

    private fun summary(context: Context, cards: Int, decks: Int, media: Int): String {
        val res = context.resources
        return context.getString(
            R.string.backup_summary,
            res.getQuantityString(R.plurals.count_cards, cards, cards),
            res.getQuantityString(R.plurals.count_decks, decks, decks),
            res.getQuantityString(R.plurals.count_media_files, media, media)
        )
    }
}
