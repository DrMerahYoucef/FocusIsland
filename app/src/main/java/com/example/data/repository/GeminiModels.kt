package com.example.data.repository

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiModelsInfo(
    val models: List<String> = emptyList(),
    val checkedAt: Long = 0L,
    val refreshing: Boolean = false,
    val failed: Boolean = false
)

/**
 * Which Gemini models the user's key can use, read from Google's model list (at most every
 * [REFRESH_MS]) and cached. The choice is either a model id or "newest of a family"
 * ([AUTO_FLASH], [AUTO_FLASH_LITE], [AUTO_PRO]); [attemptOrder] turns it into the list of models
 * to try, newest first, so a failing or retired model falls through to the next one.
 */
class GeminiModels(private val prefs: SharedPreferences, private val clock: () -> Long = System::currentTimeMillis) {

    private val _info = MutableStateFlow(
        GeminiModelsInfo(
            models = prefs.getString(KEY_MODELS, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L)
        )
    )
    val info: StateFlow<GeminiModelsInfo> = _info.asStateFlow()

    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    /**
     * Reads the list from Google unless the cached one is recent. Never throws; returns false when
     * Google could not be reached or rejected the key (the cached list is kept).
     */
    suspend fun refresh(apiKey: String, force: Boolean = false): Boolean {
        val current = _info.value
        if (apiKey.isBlank()) return false
        if (!force && current.models.isNotEmpty() && clock() - current.checkedAt < REFRESH_MS) return true
        _info.update { it.copy(refreshing = true) }
        val models = runCatching { fetch(apiKey) }.getOrNull()
        if (models.isNullOrEmpty()) {
            _info.update { it.copy(refreshing = false, failed = true) }
            return false
        }
        val now = clock()
        prefs.edit().putString(KEY_MODELS, models.joinToString(",")).putLong(KEY_CHECKED_AT, now).apply()
        _info.value = GeminiModelsInfo(models, now)
        return true
    }

    /** Models to try for [selected], best first. */
    fun attemptOrder(selected: String, extra: List<String> = emptyList()): List<String> =
        attemptOrder(selected, _info.value.models, extra)

    private suspend fun fetch(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val url = "$BASE_URL?pageSize=1000" + (pageToken?.let { "&pageToken=$it" } ?: "")
            val request = Request.Builder().url(url).header(API_KEY_HEADER, apiKey.trim()).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string().orEmpty())
                val array = json.optJSONArray("models")
                for (i in 0 until (array?.length() ?: 0)) {
                    val model = array!!.getJSONObject(i)
                    val methods = model.optJSONArray("supportedGenerationMethods")
                    val canGenerate = (0 until (methods?.length() ?: 0)).any { methods!!.optString(it) == "generateContent" }
                    if (canGenerate) ids += model.optString("name").removePrefix("models/")
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            }
        } while (pageToken != null)
        rank(ids)
    }

    companion object {
        const val AUTO_FLASH = "auto:flash"
        const val AUTO_FLASH_LITE = "auto:flash-lite"
        const val AUTO_PRO = "auto:pro"
        val AUTO_CHOICES = listOf(AUTO_FLASH, AUTO_FLASH_LITE, AUTO_PRO)

        const val REFRESH_MS = 12 * 60 * 60 * 1000L
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val KEY_MODELS = "gemini_live_models"
        private const val KEY_CHECKED_AT = "gemini_live_checked_at"

        /** Used until the list has been read once (no key yet, offline). */
        val FALLBACK = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-2.0-flash")

        // gemini-<version>-<family>[-preview…|-NNN]; image, audio, TTS, live and experimental models are left out.
        private val ID = Regex("""^gemini-(\d+(?:\.\d+)?)-(flash-lite|flash|pro)(-preview(?:-[\w.]+)*|-\d{3})?$""")

        private val NOT_TEXT = Regex("tts|image|audio|live|embed|thinking|native|computer|robotics")

        fun familyOf(id: String): String? = ID.find(id)?.groupValues?.get(2)

        /** Text models only, newest version first; at the same version stable before numbered before preview. */
        fun rank(ids: List<String>): List<String> = ids.distinct()
            .filterNot { NOT_TEXT.containsMatchIn(it) }
            .mapNotNull { id -> ID.find(id)?.let { m -> id to m } }
            .sortedWith(
                compareByDescending<Pair<String, MatchResult>> { it.second.groupValues[1].toDouble() }
                    .thenByDescending { stability(it.second.groupValues[3]) }
                    .thenBy { it.first }
            )
            .map { it.first }

        private fun stability(suffix: String) = when {
            suffix.isEmpty() -> 2
            suffix.startsWith("-preview") -> 0
            else -> 1
        }

        /**
         * For "newest of a family": that family newest first, then the other families as backups.
         * For a fixed id: that id first. [live] is the list from Google ([FALLBACK] if empty);
         * [extra] (models the user added) and [FALLBACK] close the list.
         */
        fun attemptOrder(selected: String, live: List<String>, extra: List<String> = emptyList()): List<String> {
            val pool = rank(live.ifEmpty { FALLBACK })
            val family = selected.removePrefix("auto:").takeIf { selected.startsWith("auto:") }
            val ordered = if (family != null) {
                val families = when (family) {
                    "pro" -> listOf("pro", "flash", "flash-lite")
                    "flash-lite" -> listOf("flash-lite", "flash", "pro")
                    else -> listOf("flash", "flash-lite", "pro")
                }
                pool.sortedBy { families.indexOf(familyOf(it)).let { i -> if (i < 0) families.size else i } }
            } else {
                listOf(selected) + pool
            }
            return (ordered + extra + FALLBACK).distinct()
        }
    }
}
