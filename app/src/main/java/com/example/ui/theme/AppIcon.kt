package com.example.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon follows the style: each style has a launcher alias in the manifest with its own
 * icon, and only the chosen one is enabled. Applied when the user leaves the app (MainActivity.onStop),
 * so the launcher refreshes while nobody looks at it.
 */
object AppIcon {

    private fun alias(style: AppStyle) = "com.example.Launcher" + when (style) {
        AppStyle.LAGOON -> "Lagoon"
        AppStyle.ARCADE -> "Arcade"
        AppStyle.BLOSSOM -> "Blossom"
        AppStyle.EXPLORER -> "Explorer"
    }

    /** Enables [style]'s launcher entry, then disables the others (never zero entries). No-op if already right. */
    fun apply(context: Context, style: AppStyle) {
        val pm = context.packageManager
        fun component(s: AppStyle) = ComponentName(context.packageName, alias(s))
        fun enabled(s: AppStyle) = when (pm.getComponentEnabledSetting(component(s))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> s == AppStyle.LAGOON // manifest default
            else -> false
        }
        fun set(s: AppStyle, on: Boolean) = runCatching {
            pm.setComponentEnabledSetting(
                component(s),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        if (!enabled(style)) set(style, true)
        AppStyle.entries.filter { it != style && enabled(it) }.forEach { set(it, false) }
    }
}
