package com.example.ui.screen.appblocker

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.data.db.entity.BlockedAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [icon] is a small bitmap made once off the main thread (drawing Drawables per row was slow). */
data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?
)

private const val ICON_PX = 96

class AppBlockerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FocusFlowApplication.instance.blockedAppRepository
    private val packageManager = application.packageManager

    val blockedApps: StateFlow<List<BlockedAppEntity>> =
        repository.getAllBlocked().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** null while the list of installed apps is loading. */
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>?>(null)
    val installedApps: StateFlow<List<InstalledAppInfo>?> = _installedApps.asStateFlow()

    init {
        viewModelScope.launch { _installedApps.value = loadLaunchableApps() }
    }

    /**
     * Every app with a launcher icon (including preinstalled ones like YouTube or Chrome),
     * except this app. Uses the <queries> launcher declaration, so QUERY_ALL_PACKAGES isn't needed.
     */
    private suspend fun loadLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        runCatching {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != getApplication<Application>().packageName }
                .map { info ->
                    InstalledAppInfo(
                        packageName = info.packageName,
                        appName = packageManager.getApplicationLabel(info).toString(),
                        icon = runCatching { packageManager.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
                    )
                }
                .sortedBy { it.appName.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun toggleNotifBlock(pkg: String, appName: String, block: Boolean) = update(pkg, appName) { current, isNew ->
        // When a user adds an app to the App Blocker list for the first time, block opening by default as well
        current.copy(
            blockNotifications = block,
            blockLaunch = if (isNew && block) true else current.blockLaunch
        )
    }

    fun toggleLaunchBlock(pkg: String, appName: String, block: Boolean) = update(pkg, appName) { current, _ ->
        current.copy(blockLaunch = block)
    }

    fun toggleAppBlocked(pkg: String, appName: String) = update(pkg, appName) { current, isNew ->
        if (isNew || (!current.blockLaunch && !current.blockNotifications)) {
            current.copy(blockNotifications = true, blockLaunch = true)
        } else {
            current.copy(blockNotifications = false, blockLaunch = false)
        }
    }

    /** Running services observe the table, so changes apply to an ongoing session immediately. */
    private fun update(pkg: String, appName: String, change: (BlockedAppEntity, Boolean) -> BlockedAppEntity) {
        viewModelScope.launch {
            val existing = blockedApps.value.find { it.packageName == pkg }
            val current = existing
                ?: BlockedAppEntity(pkg, appName, appIconBase64 = "", blockNotifications = false, blockLaunch = false)
            val updated = change(current, existing == null)
            if (!updated.blockNotifications && !updated.blockLaunch) repository.deleteByPackage(pkg)
            else repository.upsert(updated)
        }
    }
}
