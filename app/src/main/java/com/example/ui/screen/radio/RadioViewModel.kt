package com.example.ui.screen.radio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import com.example.data.LastSound
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.FocusFlowApplication
import com.example.data.RadioStation
import com.example.data.api.RadioBrowserClient
import com.example.data.api.RadioStationResponse
import com.example.data.db.entity.CategoryEntity
import com.example.data.db.entity.FavouriteStationEntity
import com.example.data.db.entity.StationEntity
import com.example.service.PomodoroTimerService
import com.example.service.RadioPlayerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** What the player is doing, for the now-playing bar. */
enum class PlaybackStatus { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }

/** Discover tab (Radio Browser directory). [error] means the directory was unreachable. */
data class DiscoverUiState(
    val query: String = "",
    val results: List<RadioStation> = emptyList(),
    val trending: List<RadioStation> = emptyList(),
    val searching: Boolean = false,
    val loadingTrending: Boolean = false,
    val searchError: Boolean = false,
    val trendingError: Boolean = false
)

/** Built-in category ids (seeded by DatabaseSeeder); their names are localized in the UI. */
const val CATEGORY_STUDY = "STUDY"
const val CATEGORY_GLOBAL = "GLOBAL"

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FocusFlowApplication.instance.database

    init {
        initController(application)
        viewModelScope.launch {
            // Legacy builds seeded an "ALGERIAN" category; remove it if still there. Default rows are
            // seeded by DatabaseSeeder (database creation and reset), not here.
            runCatching {
                db.radioDao().deleteStationsByCategoryId("ALGERIAN")
                db.radioDao().deleteCategoryForce("ALGERIAN")
            }
        }
    }

    val favouriteIds: StateFlow<Set<String>> =
        db.favouriteStationDao().getAllFavourites()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val categories: StateFlow<List<CategoryEntity>> =
        db.radioDao().getAllCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every saved station, favourites first, then by name. */
    val displayedStations: StateFlow<List<RadioStation>> =
        combine(db.radioDao().getAllStations(), favouriteIds) { stations, favs ->
            stations.map { it.toRadioStation() }.sortedByDescending { it.id in favs }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = application.getSharedPreferences(LastSound.PREFS, Context.MODE_PRIVATE)

    // The last station is remembered so the Focus tab can offer it again after a restart.
    private val _currentStation = MutableStateFlow(LastSound.station(prefs))
    val currentStation = _currentStation.asStateFlow()

    private val _status = MutableStateFlow(PlaybackStatus.IDLE)
    val status = _status.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    /** The list the current station was started from; previous/next move through it. */
    private var queue: List<RadioStation> = emptyList()

    private var mediaController: MediaController? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()

    // ------------------------------------------------------------------ playback

    private fun initController(context: Context) {
        if (mediaController != null) return
        val token = SessionToken(context, ComponentName(context, RadioPlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        refreshStatus(controller)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) = refreshStatus(controller)

                    override fun onPlayerError(error: PlaybackException) {
                        _isPlaying.value = false
                        _status.value = PlaybackStatus.ERROR
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.toRadioStation()?.let { s ->
                            if (_currentStation.value?.id != s.id) _currentStation.value = s
                        }
                    }
                })
                _isPlaying.value = controller.isPlaying
                controller.currentMediaItem?.toRadioStation()?.let { _currentStation.value = it }
                refreshStatus(controller)
                val actions = ArrayList(pendingActions)
                pendingActions.clear()
                actions.forEach { it(controller) }
            } catch (e: Exception) {
                android.util.Log.e("RadioViewModel", "Media controller unavailable", e)
            }
        }, context.mainExecutor)
    }

    private fun refreshStatus(controller: Player) {
        if (controller.playerError != null) { _status.value = PlaybackStatus.ERROR; return }
        _status.value = when {
            controller.isPlaying -> PlaybackStatus.PLAYING
            controller.playbackState == Player.STATE_BUFFERING && controller.playWhenReady -> PlaybackStatus.BUFFERING
            controller.mediaItemCount > 0 -> PlaybackStatus.PAUSED
            else -> PlaybackStatus.IDLE
        }
    }

    private fun whenReady(context: Context, action: (MediaController) -> Unit) {
        initController(context)
        mediaController?.let(action) ?: pendingActions.add(action)
    }

    /** Plays [station]. [from] is the list it was picked in, used by previous / next. */
    fun selectStation(station: RadioStation, context: Context, from: List<RadioStation> = emptyList()) {
        if (from.isNotEmpty()) queue = from
        _currentStation.value = station
        LastSound.saveStation(prefs, station)
        _status.value = PlaybackStatus.BUFFERING
        stopAmbient(context)
        whenReady(context) { controller ->
            // Was hardcoded to another package name, so the fallback artwork never loaded.
            val artwork = station.logoUrl.ifEmpty { "android.resource://${context.packageName}/drawable/ic_radio_default" }
            val item = MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist(station.country)
                        .setDescription(station.description)
                        .setArtworkUri(Uri.parse(artwork))
                        .build()
                )
                .build()
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()
        }
    }

    /** Next (+1) or previous (-1) station in the list the current one was started from. */
    fun skip(context: Context, direction: Int) {
        val list = queue.ifEmpty { displayedStations.value.filter { it.id in favouriteIds.value } }
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.id == _currentStation.value?.id }
        val next = list[((if (index < 0) 0 else index + direction) % list.size + list.size) % list.size]
        selectStation(next, context)
    }

    fun togglePlayback(context: Context) {
        whenReady(context) { controller ->
            when {
                controller.isPlaying -> controller.pause()
                controller.playerError != null || controller.mediaItemCount == 0 ->
                    _currentStation.value?.let { selectStation(it, context) }
                else -> {
                    stopAmbient(context)
                    LastSound.setKind(prefs, LastSound.KIND_RADIO)
                    controller.play()
                }
            }
        }
    }

    /** Radio and ambient sounds never play together. */
    private fun stopAmbient(context: Context) {
        runCatching {
            context.startService(
                Intent(context, PomodoroTimerService::class.java).apply {
                    action = PomodoroTimerService.ACTION_SET_AMBIENT
                    putExtra(PomodoroTimerService.EXTRA_AMBIENT_ID, PomodoroTimerService.AMBIENT_NONE)
                }
            )
        }
    }

    fun pausePlayback(context: Context) {
        whenReady(context) { if (it.isPlaying) it.pause() }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaController = null
    }

    // ------------------------------------------------------------------ library

    fun toggleFavourite(station: RadioStation) {
        viewModelScope.launch {
            val dao = db.favouriteStationDao()
            if (dao.isFavourite(station.id) > 0) {
                dao.remove(station.id)
            } else {
                // Save a station found in Discover so it plays offline from the list. An existing row is
                // left untouched (re-inserting used to move built-in stations to Study and mark them custom).
                if (db.radioDao().getStation(station.id) == null) {
                    db.radioDao().insertStation(station.toEntity(categoryId = CATEGORY_STUDY).copy(isCustom = true))
                }
                dao.add(FavouriteStationEntity(station.id))
            }
        }
    }

    fun addCustomCategory(name: String, onCreated: (String) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            db.radioDao().insertCategory(CategoryEntity(id = id, name = name.trim(), isCustom = true))
            onCreated(id)
        }
    }

    /** Deletes a custom category and the stations in it (built-in categories are protected by the DAO). */
    fun removeCategory(id: String) {
        viewModelScope.launch { db.radioDao().deleteCategoryAndStations(id) }
    }

    fun addCustomStation(name: String, url: String, categoryId: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            db.radioDao().insertStation(
                StationEntity(
                    id = id,
                    name = name.trim(),
                    country = "",
                    categoryId = categoryId,
                    streamUrl = url.trim(),
                    description = "",
                    isCustom = true
                )
            )
            db.favouriteStationDao().add(FavouriteStationEntity(id))
        }
    }

    /** Removes a station you added or saved. Returns whether it was a favourite, for Undo. */
    fun removeStation(station: RadioStation, onRemoved: (wasFavourite: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val wasFavourite = db.favouriteStationDao().isFavourite(station.id) > 0
            db.radioDao().deleteCustomStation(station.id)
            db.favouriteStationDao().remove(station.id)
            onRemoved(wasFavourite)
        }
    }

    fun restoreStation(station: RadioStation, wasFavourite: Boolean) {
        viewModelScope.launch {
            val category = station.categoryId.takeIf { id -> categories.value.any { it.id == id } } ?: CATEGORY_STUDY
            db.radioDao().insertStation(station.toEntity(category).copy(isCustom = true))
            if (wasFavourite) db.favouriteStationDao().add(FavouriteStationEntity(station.id))
        }
    }

    fun saveDiscoveredStation(station: RadioStation, categoryId: String) {
        viewModelScope.launch {
            db.radioDao().insertStation(station.toEntity(categoryId).copy(isCustom = true))
        }
    }

    // ------------------------------------------------------------------ discover

    private val _discover = MutableStateFlow(DiscoverUiState())
    val discover = _discover.asStateFlow()
    private var searchJob: Job? = null

    /** Searches the directory 400 ms after the last keystroke; earlier requests are cancelled. */
    fun search(query: String) {
        _discover.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _discover.update { it.copy(results = emptyList(), searching = false, searchError = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _discover.update { it.copy(searching = true, searchError = false) }
            val results = runCatching { RadioBrowserClient.api.searchStations(name = query.trim(), limit = 40) }
            _discover.update {
                results.fold(
                    onSuccess = { list -> it.copy(results = list.toPlayable(), searching = false) },
                    onFailure = { _ -> it.copy(results = builtInStations(), searching = false, searchError = true) }
                )
            }
        }
    }

    fun retrySearch() = search(_discover.value.query)

    fun loadTrending(force: Boolean = false) {
        val state = _discover.value
        if (!force && (state.trending.isNotEmpty() || state.loadingTrending)) return
        viewModelScope.launch {
            _discover.update { it.copy(loadingTrending = true, trendingError = false) }
            val result = runCatching { RadioBrowserClient.api.getTopClick(limit = 40) }
            _discover.update {
                result.fold(
                    onSuccess = { list -> it.copy(trending = list.toPlayable(), loadingTrending = false) },
                    onFailure = { _ -> it.copy(trending = builtInStations(), loadingTrending = false, trendingError = true) }
                )
            }
        }
    }

    /** Shown when the directory is down: the app's own stations, which are known to work. */
    private suspend fun builtInStations(): List<RadioStation> =
        runCatching { db.radioDao().getBuiltInStations().map { it.toRadioStation() } }.getOrDefault(emptyList())
}

/**
 * Directory results the app can actually play: Android blocks plain-http streams for this app,
 * so those are left out instead of failing when tapped.
 */
private fun List<RadioStationResponse>.toPlayable(): List<RadioStation> = mapNotNull { r ->
    val url = (r.urlResolved ?: r.url)?.trim().orEmpty()
    if (!url.startsWith("https://", ignoreCase = true)) return@mapNotNull null
    RadioStation(
        id = r.stationuuid,
        name = r.name.trim().ifBlank { r.stationuuid.take(8) },
        country = r.country?.trim().orEmpty(),
        categoryId = "",
        streamUrl = url,
        logoUrl = r.favicon?.takeIf { it.startsWith("https://") }.orEmpty(),
        description = r.tags?.split(',')?.take(3)?.joinToString(", ") { it.trim() }?.ifBlank { null } ?: r.language.orEmpty(),
        isCustom = false
    )
}

private fun MediaItem.toRadioStation(): RadioStation? {
    val meta = mediaMetadata
    val title = meta.title?.toString() ?: return null
    return RadioStation(
        id = mediaId,
        name = title,
        country = meta.artist?.toString().orEmpty(),
        categoryId = "",
        streamUrl = localConfiguration?.uri?.toString().orEmpty(),
        logoUrl = meta.artworkUri?.toString()?.takeIf { it.startsWith("http") }.orEmpty(),
        description = meta.description?.toString().orEmpty(),
        isCustom = false
    )
}

private fun RadioStation.toEntity(categoryId: String) = StationEntity(
    id = id,
    name = name,
    country = country,
    categoryId = categoryId,
    streamUrl = streamUrl,
    fallbackUrl = fallbackUrl,
    logoUrl = logoUrl,
    description = description,
    isCustom = isCustom
)

/** Maps a DB row to the UI model. */
fun StationEntity.toRadioStation(): RadioStation = RadioStation(
    id = id,
    name = name,
    country = country,
    categoryId = categoryId,
    streamUrl = streamUrl,
    fallbackUrl = fallbackUrl,
    logoUrl = logoUrl,
    description = description,
    isCustom = isCustom
)
