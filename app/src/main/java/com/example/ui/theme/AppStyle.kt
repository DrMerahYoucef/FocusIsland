package com.example.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import com.example.R

/**
 * The app's look, chosen in Settings → Appearance → Style. Purely visual: every screen, feature and
 * piece of data is the same in every style. Stored as [key] under [PREF_KEY]; Lagoon by default.
 */
enum class AppStyle(val key: String, @StringRes val label: Int, @StringRes val hint: Int) {
    LAGOON("lagoon", R.string.style_lagoon, R.string.style_lagoon_hint),
    ARCADE("arcade", R.string.style_arcade, R.string.style_arcade_hint),
    BLOSSOM("blossom", R.string.style_blossom, R.string.style_blossom_hint),
    EXPLORER("explorer", R.string.style_explorer, R.string.style_explorer_hint);

    companion object {
        const val PREF_KEY = "app_style"
        /** Set once the "choose your style" tip was opened or dismissed. */
        const val PREF_TIP_SEEN = "app_style_tip_seen"
        private const val PREFS = "focusflow_prefs"

        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: LAGOON

        /** For code outside Compose (wallpaper, widgets, services). */
        fun read(context: Context): AppStyle =
            from(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_KEY, null))
    }
}
