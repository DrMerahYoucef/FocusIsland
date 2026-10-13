package com.example.data

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * What the Focus tab's sound card offers again: the last radio station and the last ambient
 * sound, and which of the two was used most recently. Stored in the app's main preferences.
 */
object LastSound {
    const val PREFS = "focusflow_prefs"
    const val KEY_KIND = "last_sound_kind"
    const val KEY_AMBIENT = "last_ambient_id"
    private const val KEY_STATION = "last_radio_station"

    const val KIND_RADIO = "radio"
    const val KIND_AMBIENT = "ambient"

    fun setKind(prefs: SharedPreferences, kind: String) = prefs.edit().putString(KEY_KIND, kind).apply()

    fun saveStation(prefs: SharedPreferences, s: RadioStation) {
        val json = JSONObject()
            .put("id", s.id).put("name", s.name).put("country", s.country).put("categoryId", s.categoryId)
            .put("streamUrl", s.streamUrl).put("fallbackUrl", s.fallbackUrl).put("logoUrl", s.logoUrl)
            .put("description", s.description).put("isCustom", s.isCustom)
        prefs.edit().putString(KEY_STATION, json.toString()).putString(KEY_KIND, KIND_RADIO).apply()
    }

    fun station(prefs: SharedPreferences): RadioStation? = runCatching {
        val o = JSONObject(prefs.getString(KEY_STATION, null) ?: return null)
        RadioStation(
            id = o.getString("id"),
            name = o.optString("name"),
            country = o.optString("country"),
            categoryId = o.optString("categoryId"),
            streamUrl = o.getString("streamUrl"),
            fallbackUrl = o.optString("fallbackUrl"),
            logoUrl = o.optString("logoUrl"),
            description = o.optString("description"),
            isCustom = o.optBoolean("isCustom")
        )
    }.getOrNull()

    fun saveAmbient(prefs: SharedPreferences, ambientId: String) =
        prefs.edit().putString(KEY_AMBIENT, ambientId).putString(KEY_KIND, KIND_AMBIENT).apply()
}
