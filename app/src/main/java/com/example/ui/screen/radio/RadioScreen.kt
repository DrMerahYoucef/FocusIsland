package com.example.ui.screen.radio

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.data.RadioStation
import com.example.data.db.entity.CategoryEntity
import com.example.ui.designsystem.BannerTone
import com.example.ui.designsystem.ConfirmDialog
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.InfoBanner
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.ThemePreviews
import com.example.ui.theme.FocusIslandTheme
import com.example.ui.theme.Spacing
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private const val FILTER_FAVOURITES = "__favourites"
private const val FILTER_ALL = "__all"

/**
 * Radio: "My stations" (favourites, all, or one category; add stations and categories) and
 * "Discover" (search the worldwide directory, popular stations, save to a category).
 * The now-playing bar stays at the bottom on both tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    onBack: () -> Unit,
    viewModel: RadioViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val stations by viewModel.displayedStations.collectAsStateWithLifecycle()
    val favourites by viewModel.favouriteIds.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val current by viewModel.currentStation.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val discover by viewModel.discover.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(FILTER_FAVOURITES) }
    var showAddStation by rememberSaveable { mutableStateOf(false) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }
    var deleteCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var saveTarget by remember { mutableStateOf<RadioStation?>(null) }

    // A deleted category can't stay selected.
    LaunchedEffect(categories, filter) {
        if (filter != FILTER_FAVOURITES && filter != FILTER_ALL && categories.isNotEmpty() && categories.none { it.id == filter }) {
            filter = FILTER_FAVOURITES
        }
    }

    val msgUndo = stringResource(R.string.radio_undo)
    val msgSaved = stringResource(R.string.radio_station_saved)
    val play: (RadioStation, List<RadioStation>) -> Unit = { s, list -> viewModel.selectStation(s, context, list) }
    val remove: (RadioStation) -> Unit = { s ->
        viewModel.removeStation(s) { wasFavourite ->
            scope.launch {
                val result = snackbar.showSnackbar(context.getString(R.string.radio_station_removed, s.name), msgUndo, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreStation(s, wasFavourite)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.radio_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddStation = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.radio_add_station))
                    }
                }
            )
        },
        bottomBar = {
            current?.let { s ->
                NowPlayingBar(
                    station = s,
                    status = status,
                    isFavourite = s.id in favourites,
                    onToggle = { viewModel.togglePlayback(context) },
                    onPrevious = { viewModel.skip(context, -1) },
                    onNext = { viewModel.skip(context, +1) },
                    onFavourite = { viewModel.toggleFavourite(s) }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.radio_tab_mine)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.radio_tab_discover)) })
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val listModifier = Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize()
                if (tab == 0) {
                    val visible = remember(stations, favourites, filter) {
                        when (filter) {
                            FILTER_FAVOURITES -> stations.filter { it.id in favourites }
                            FILTER_ALL -> stations
                            else -> stations.filter { it.categoryId == filter }
                        }
                    }
                    val selectedCategory = categories.firstOrNull { it.id == filter }
                    MyStations(
                        stations = visible,
                        categories = categories,
                        filter = filter,
                        onFilter = { filter = it },
                        selectedCategory = selectedCategory,
                        favourites = favourites,
                        currentId = current?.id,
                        isPlaying = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING,
                        onPlay = { play(it, visible) },
                        onFavourite = viewModel::toggleFavourite,
                        onRemove = remove,
                        onNewCategory = { showNewCategory = true },
                        onDeleteCategory = { deleteCategoryId = it },
                        onAddStation = { showAddStation = true },
                        onDiscover = { tab = 1 },
                        modifier = listModifier
                    )
                } else {
                    LaunchedEffect(Unit) { viewModel.loadTrending() }
                    DiscoverTab(
                        state = discover,
                        favourites = favourites,
                        currentId = current?.id,
                        isPlaying = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING,
                        onQuery = viewModel::search,
                        onRetrySearch = viewModel::retrySearch,
                        onRetryTrending = { viewModel.loadTrending(force = true) },
                        onPlay = play,
                        onFavourite = viewModel::toggleFavourite,
                        onSaveTo = { saveTarget = it },
                        modifier = listModifier
                    )
                }
            }
        }
    }

    if (showAddStation) {
        AddStationDialog(
            categories = categories,
            initialCategory = categories.firstOrNull { it.id == filter }?.id ?: CATEGORY_STUDY,
            onConfirm = { name, url, categoryId ->
                viewModel.addCustomStation(name, url, categoryId)
                showAddStation = false
                scope.launch { snackbar.showSnackbar(msgSaved) }
            },
            onDismiss = { showAddStation = false }
        )
    }
    if (showNewCategory) {
        NameDialog(
            title = stringResource(R.string.radio_new_category),
            label = stringResource(R.string.radio_field_category_name),
            confirmLabel = stringResource(R.string.study_create),
            onConfirm = { name ->
                viewModel.addCustomCategory(name) { id -> filter = id }
                showNewCategory = false
            },
            onDismiss = { showNewCategory = false }
        )
    }
    deleteCategoryId?.let { id ->
        val category = categories.firstOrNull { it.id == id }
        val count = stations.count { it.categoryId == id }
        if (category != null) {
            ConfirmDialog(
                title = stringResource(R.string.radio_delete_category_title, category.name),
                body = pluralStringResource(R.plurals.radio_delete_category_body, count, count),
                confirmLabel = stringResource(R.string.delete),
                dismissLabel = stringResource(R.string.cancel),
                onConfirm = {
                    viewModel.removeCategory(id)
                    deleteCategoryId = null
                    filter = FILTER_FAVOURITES
                },
                onDismiss = { deleteCategoryId = null },
                destructive = true
            )
        }
    }
    saveTarget?.let { station ->
        SaveToCategoryDialog(
            categories = categories,
            onConfirm = { categoryId ->
                viewModel.saveDiscoveredStation(station, categoryId)
                saveTarget = null
                scope.launch { snackbar.showSnackbar(msgSaved) }
            },
            onDismiss = { saveTarget = null }
        )
    }
}

// ---------------------------------------------------------------- My stations

@Composable
private fun MyStations(
    stations: List<RadioStation>,
    categories: List<CategoryEntity>,
    filter: String,
    onFilter: (String) -> Unit,
    selectedCategory: CategoryEntity?,
    favourites: Set<String>,
    currentId: String?,
    isPlaying: Boolean,
    onPlay: (RadioStation) -> Unit,
    onFavourite: (RadioStation) -> Unit,
    onRemove: (RadioStation) -> Unit,
    onNewCategory: () -> Unit,
    onDeleteCategory: (String) -> Unit,
    onAddStation: () -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = Spacing.lg)) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                item {
                    FilterChip(
                        selected = filter == FILTER_FAVOURITES,
                        onClick = { onFilter(FILTER_FAVOURITES) },
                        label = { Text(stringResource(R.string.radio_filter_favourites)) },
                        leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(Spacing.iconSm - Spacing.xxs)) }
                    )
                }
                item { FilterChip(selected = filter == FILTER_ALL, onClick = { onFilter(FILTER_ALL) }, label = { Text(stringResource(R.string.radio_filter_all)) }) }
                items(categories, key = { it.id }) { c ->
                    FilterChip(selected = filter == c.id, onClick = { onFilter(c.id) }, label = { Text(categoryLabel(c)) })
                }
                item {
                    AssistChip(
                        onClick = onNewCategory,
                        label = { Text(stringResource(R.string.radio_new_category)) },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(Spacing.iconSm - Spacing.xxs)) }
                    )
                }
            }
        }
        if (selectedCategory?.isCustom == true) {
            item {
                SectionHeader(categoryLabel(selectedCategory), modifier = Modifier.padding(horizontal = Spacing.md)) {
                    TextButton(onClick = { onDeleteCategory(selectedCategory.id) }) {
                        Text(stringResource(R.string.radio_delete_category), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        if (stations.isEmpty()) {
            item {
                if (filter == FILTER_FAVOURITES) {
                    EmptyState(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = stringResource(R.string.radio_empty_favourites_title),
                        body = stringResource(R.string.radio_empty_favourites_body),
                        actionLabel = stringResource(R.string.radio_tab_discover),
                        onAction = onDiscover
                    )
                } else {
                    EmptyState(
                        icon = Icons.Rounded.Radio,
                        title = stringResource(R.string.radio_empty_category_title),
                        body = stringResource(R.string.radio_empty_category_body),
                        actionLabel = stringResource(R.string.radio_add_station),
                        onAction = onAddStation
                    )
                }
            }
        } else {
            items(stations, key = { it.id }) { s ->
                StationRow(
                    station = s,
                    isFavourite = s.id in favourites,
                    isCurrent = s.id == currentId,
                    isPlaying = isPlaying,
                    onPlay = { onPlay(s) },
                    onFavourite = { onFavourite(s) },
                    onRemove = if (s.isCustom) ({ onRemove(s) }) else null
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Discover

@Composable
private fun DiscoverTab(
    state: DiscoverUiState,
    favourites: Set<String>,
    currentId: String?,
    isPlaying: Boolean,
    onQuery: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onRetryTrending: () -> Unit,
    onPlay: (RadioStation, List<RadioStation>) -> Unit,
    onFavourite: (RadioStation) -> Unit,
    onSaveTo: (RadioStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val searching = state.query.trim().length >= 2
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = Spacing.lg)) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.radio_search)) },
                placeholder = { Text(stringResource(R.string.radio_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (state.query.isNotEmpty()) {
                    { IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.community_clear)) } }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
        if (searching) {
            stationSection(
                title = R.string.radio_results,
                loading = state.searching,
                error = state.searchError,
                errorText = R.string.radio_directory_down_search,
                onRetry = onRetrySearch,
                stations = state.results,
                emptyContent = {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.radio_no_results_title),
                        body = stringResource(R.string.radio_no_results_body)
                    )
                },
                favourites = favourites, currentId = currentId, isPlaying = isPlaying,
                onPlay = onPlay, onFavourite = onFavourite, onSaveTo = onSaveTo
            )
        } else {
            stationSection(
                title = R.string.radio_popular,
                loading = state.loadingTrending,
                error = state.trendingError,
                errorText = R.string.radio_directory_down_popular,
                onRetry = onRetryTrending,
                stations = state.trending,
                emptyContent = {},
                favourites = favourites, currentId = currentId, isPlaying = isPlaying,
                onPlay = onPlay, onFavourite = onFavourite, onSaveTo = onSaveTo
            )
        }
    }
}

private fun LazyListScope.stationSection(
    title: Int,
    loading: Boolean,
    error: Boolean,
    errorText: Int,
    onRetry: () -> Unit,
    stations: List<RadioStation>,
    emptyContent: @Composable () -> Unit,
    favourites: Set<String>,
    currentId: String?,
    isPlaying: Boolean,
    onPlay: (RadioStation, List<RadioStation>) -> Unit,
    onFavourite: (RadioStation) -> Unit,
    onSaveTo: (RadioStation) -> Unit
) {
    item { SectionHeader(stringResource(title), modifier = Modifier.padding(horizontal = Spacing.md)) }
    if (error) {
        item {
            InfoBanner(
                text = stringResource(errorText),
                tone = BannerTone.Warning,
                actionLabel = stringResource(R.string.radio_retry),
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
    }
    when {
        loading -> item {
            Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        stations.isEmpty() -> item { emptyContent() }
        else -> items(stations, key = { "d_" + it.id }) { s ->
            StationRow(
                station = s,
                isFavourite = s.id in favourites,
                isCurrent = s.id == currentId,
                isPlaying = isPlaying,
                onPlay = { onPlay(s, stations) },
                onFavourite = { onFavourite(s) },
                onSaveTo = { onSaveTo(s) }
            )
        }
    }
}

// ---------------------------------------------------------------- rows and bars

/** One station: tap to play; favourite toggle; overflow menu with Remove or Save to category when given. */
@Composable
fun StationRow(
    station: RadioStation,
    isFavourite: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onFavourite: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onSaveTo: (() -> Unit)? = null
) {
    val playingNow = isCurrent && isPlaying
    val nowPlaying = stringResource(R.string.radio_now_playing)
    ListItem(
        leadingContent = { StationArt(station, playing = playingNow) },
        headlineContent = { Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(stationSubtitle(station), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FavouriteButton(isFavourite, onFavourite)
                if (onRemove != null || onSaveTo != null) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { open = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.radio_station_options, station.name))
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            if (onSaveTo != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.radio_save_to)) },
                                    leadingIcon = { Icon(Icons.Rounded.LibraryAdd, contentDescription = null) },
                                    onClick = { open = false; onSaveTo() }
                                )
                            }
                            if (onRemove != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.radio_remove_station), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { open = false; onRemove() }
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.radio_play), onClick = onPlay)
            .semantics { if (playingNow) stateDescription = nowPlaying }
    )
}

@Composable
private fun FavouriteButton(isFavourite: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = isFavourite, onCheckedChange = { onToggle() }) {
        Icon(
            if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(R.string.radio_favourite),
            tint = if (isFavourite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Station logo, or a radio icon on a tonal tile; an equaliser icon while it plays. */
@Composable
internal fun StationArt(station: RadioStation, playing: Boolean, size: androidx.compose.ui.unit.Dp = Spacing.xxl) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (station.logoUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = station.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                    error = { Icon(Icons.Rounded.Radio, contentDescription = null) },
                    loading = { Icon(Icons.Rounded.Radio, contentDescription = null) }
                )
            } else {
                Icon(Icons.Rounded.Radio, contentDescription = null)
            }
            if (playing) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
    }
}

/** Pinned player: artwork, name, status, previous / play-pause / next, favourite. */
@Composable
fun NowPlayingBar(
    station: RadioStation,
    status: PlaybackStatus,
    isFavourite: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavourite: () -> Unit
) {
    val statusText = stringResource(
        when (status) {
            PlaybackStatus.PLAYING -> R.string.radio_status_playing
            PlaybackStatus.BUFFERING -> R.string.radio_status_connecting
            PlaybackStatus.ERROR -> R.string.radio_status_error
            else -> R.string.radio_status_paused
        }
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = Spacing.xxs / 2) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StationArt(station, playing = false)
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == PlaybackStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            FavouriteButton(isFavourite, onFavourite)
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.radio_previous)) }
            FilledIconButton(onClick = onToggle) {
                when {
                    status == PlaybackStatus.BUFFERING -> CircularProgressIndicator(
                        strokeWidth = Spacing.progressStroke,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(Spacing.iconSm).semantics { contentDescription = statusText }
                    )
                    status == PlaybackStatus.ERROR -> Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.radio_retry))
                    status == PlaybackStatus.PLAYING -> Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.radio_pause))
                    else -> Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.radio_play))
                }
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.radio_next)) }
        }
    }
}

// ---------------------------------------------------------------- dialogs

/** Adds a stream by link. Only https links are accepted: Android blocks plain http for this app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStationDialog(
    categories: List<CategoryEntity>,
    initialCategory: String,
    onConfirm: (name: String, url: String, categoryId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(initialCategory) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val nameError = submitted && name.isBlank()
    val urlError = submitted && streamUrlError(url) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.radio_add_station)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.radio_field_name)) },
                    isError = nameError,
                    supportingText = if (nameError) { { Text(stringResource(R.string.study_error_deck_name)) } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.radio_field_url)) },
                    placeholder = { Text(stringResource(R.string.radio_field_url_example)) },
                    isError = urlError,
                    supportingText = { Text(stringResource(if (urlError) streamUrlError(url)!! else R.string.radio_field_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == categoryId }?.let { categoryLabel(it) }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.radio_field_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(categoryLabel(c)) }, onClick = { categoryId = c.id; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                submitted = true
                if (name.isNotBlank() && streamUrlError(url) == null && categories.any { it.id == categoryId }) onConfirm(name, url, categoryId)
            }) { Text(stringResource(R.string.radio_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Error message for a stream link, or null when it can be added. */
internal fun streamUrlError(url: String): Int? {
    val u = url.trim()
    return when {
        u.isEmpty() -> R.string.radio_error_url_empty
        u.startsWith("http://", ignoreCase = true) -> R.string.radio_error_url_http
        !u.startsWith("https://", ignoreCase = true) || !Patterns.WEB_URL.matcher(u).matches() -> R.string.radio_error_url_invalid
        else -> null
    }
}

@Composable
private fun NameDialog(title: String, label: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = false },
                label = { Text(label) },
                isError = error,
                supportingText = if (error) { { Text(stringResource(R.string.study_error_deck_name)) } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { if (name.isBlank()) error = true else onConfirm(name) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SaveToCategoryDialog(categories: List<CategoryEntity>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.radio_save_to)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                categories.forEach { c ->
                    ListItem(
                        headlineContent = { Text(categoryLabel(c)) },
                        leadingContent = { RadioButton(selected = selected == c.id, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.selectable(selected = selected == c.id, role = Role.RadioButton, onClick = { selected = c.id })
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------- labels

/** Built-in categories are shown in the app's language; custom ones as typed. */
@Composable
fun categoryLabel(c: CategoryEntity): String = when (c.id) {
    CATEGORY_STUDY -> stringResource(R.string.radio_cat_study)
    CATEGORY_GLOBAL -> stringResource(R.string.radio_cat_global)
    else -> c.name
}

@Composable
private fun stationSubtitle(s: RadioStation): String {
    val parts = listOf(s.country, s.description).map { it.trim() }.filter { it.isNotEmpty() && it != "Custom Stream" && it != "Custom user-added stream" }
    return parts.joinToString(" · ").ifEmpty { stringResource(if (s.isCustom) R.string.radio_added_by_you else R.string.radio_live_stream) }
}

// ---------------------------------------------------------------- previews

private val previewStation = RadioStation(
    id = "p", name = "Lo-Fi Hip Hop", country = "Global", categoryId = CATEGORY_STUDY,
    streamUrl = "https://example.com/s.mp3", description = "Chill beats to study to"
)

@ThemePreviews
@Composable
private fun StationRowPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Column {
            StationRow(previewStation, isFavourite = true, isCurrent = true, isPlaying = true, onPlay = {}, onFavourite = {})
            StationRow(previewStation.copy(id = "q", name = "My stream", isCustom = true, country = "", description = ""), isFavourite = false, isCurrent = false, isPlaying = false, onPlay = {}, onFavourite = {}, onRemove = {})
        }
    }
}

@ThemePreviews
@Composable
private fun NowPlayingBarPreview() {
    FocusIslandTheme(themeMode = ThemeMode.SYSTEM) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            NowPlayingBar(previewStation, PlaybackStatus.PLAYING, isFavourite = true, onToggle = {}, onPrevious = {}, onNext = {}, onFavourite = {})
            NowPlayingBar(previewStation, PlaybackStatus.ERROR, isFavourite = false, onToggle = {}, onPrevious = {}, onNext = {}, onFavourite = {})
        }
    }
}
