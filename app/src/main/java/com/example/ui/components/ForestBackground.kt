package com.example.ui.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.ui.theme.WallpaperTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ForestState(
    val treeCount: Int = 0,
    val points: Int = 0,
    val followSystemTheme: Boolean = true,
    val manualTheme: WallpaperTheme = WallpaperTheme.LIGHT,
    val isDarkTheme: Boolean = false
)

class ForestViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusFlowApplication.instance.sessionRepository
    private val sharedPrefs = application.getSharedPreferences("focusflow_prefs", android.content.Context.MODE_PRIVATE)

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "follow_system_theme" || key == "wallpaper_theme" || key == "auto_sync_wallpaper") {
            reloadPreferences()
        }
    }

    private val _forestState = MutableStateFlow(loadInitialState())
    val forestState: StateFlow<ForestState> = _forestState.asStateFlow()

    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        viewModelScope.launch {
            repository.getAllSessions()
                .map { sessions -> com.example.data.stats.StatsCalculator.totals(sessions) }
                .collect { totals ->
                val count = totals.completedSessions
                val pts = totals.points
                val changed = count != sharedPrefs.getInt("last_synced_tree_count", 0) ||
                    pts != sharedPrefs.getInt("last_synced_points", 0)
                _forestState.update { it.copy(treeCount = count, points = pts) }
                sharedPrefs.edit()
                    .putInt("last_synced_tree_count", count)
                    .putInt("last_synced_points", pts)
                    .apply()
                // Refresh the wallpaper if the user asked for automatic updates.
                if (changed) checkAndTriggerAutoWallpaper(_forestState.value.isDarkTheme)
            }
        }
    }

    private fun loadInitialState(): ForestState {
        val followSystem = sharedPrefs.getBoolean("follow_system_theme", true)
        val manualThemeStr = sharedPrefs.getString("wallpaper_theme", "LIGHT") ?: "LIGHT"
        val manualTheme = try {
            WallpaperTheme.valueOf(manualThemeStr)
        } catch (e: Exception) {
            WallpaperTheme.LIGHT
        }
        val savedCount = sharedPrefs.getInt("last_synced_tree_count", 0)
        val savedPoints = sharedPrefs.getInt("last_synced_points", 0)
        return ForestState(
            treeCount = savedCount,
            points = savedPoints,
            followSystemTheme = followSystem,
            manualTheme = manualTheme,
            isDarkTheme = manualTheme == WallpaperTheme.DARK
        )
    }

    private fun reloadPreferences() {
        val followSystem = sharedPrefs.getBoolean("follow_system_theme", true)
        val manualThemeStr = sharedPrefs.getString("wallpaper_theme", "LIGHT") ?: "LIGHT"
        val manualTheme = try {
            WallpaperTheme.valueOf(manualThemeStr)
        } catch (e: Exception) {
            WallpaperTheme.LIGHT
        }
        _forestState.update {
            it.copy(
                followSystemTheme = followSystem,
                manualTheme = manualTheme
            )
        }
    }

    fun updateSystemDarkTheme(isSystemDark: Boolean) {
        val current = _forestState.value
        val effectiveDark = if (current.followSystemTheme) isSystemDark else (current.manualTheme == WallpaperTheme.DARK)
        if (current.isDarkTheme != effectiveDark) {
            _forestState.update { it.copy(isDarkTheme = effectiveDark) }
            checkAndTriggerAutoWallpaper(effectiveDark)
        }
    }

    fun checkAndTriggerAutoWallpaper(isDark: Boolean) {
        val app = getApplication<Application>()
        val autoSync = sharedPrefs.getBoolean("auto_sync_wallpaper", false)
        if (!autoSync) return

        val setHome = sharedPrefs.getBoolean("wallpaper_home_screen", true)
        val setLock = sharedPrefs.getBoolean("wallpaper_lock_screen", false)
        val theme = if (isDark) WallpaperTheme.DARK else WallpaperTheme.LIGHT

        val sessions = _forestState.value.treeCount
        val points = _forestState.value.points
        viewModelScope.launch {
            WallpaperHelper.setIslandWallpaper(app, theme, setHome, setLock, sessions, points)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}
