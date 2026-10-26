package com.example.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.FocusFlowApplication
import com.example.R
import com.example.data.auth.AccountState
import com.example.ui.components.ForestViewModel
import com.example.ui.designsystem.EmptyState
import com.example.ui.screen.analytics.AnalyticsScreen
import com.example.ui.screen.analytics.AnalyticsViewModel
import com.example.ui.screen.appblocker.AppBlockerScreen
import com.example.ui.screen.auth.AuthMode
import com.example.ui.screen.auth.AuthScreen
import com.example.ui.screen.challenge.ChallengeViewModel
import com.example.ui.screen.challenge.ChallengesScreen
import com.example.ui.screen.community.CommunityScreen
import com.example.ui.screen.community.CommunityViewModel
import com.example.ui.screen.exams.ExamsScreen
import com.example.ui.screen.exams.ExamsViewModel
import com.example.ui.screen.radio.RadioScreen
import com.example.ui.screen.revisions.CaptureScreen
import com.example.ui.screen.revisions.CropEditorScreen
import com.example.ui.screen.revisions.RevisionDeckDetailScreen
import com.example.ui.screen.revisions.RevisionNoteDetailScreen
import com.example.ui.screen.revisions.RevisionSessionScreen
import com.example.ui.screen.revisions.RevisionsHomeScreen
import com.example.ui.screen.revisions.RevisionsViewModel
import com.example.ui.screen.settings.SettingsScreen
import com.example.ui.screen.settings.StylePickerScreen
import com.example.ui.screen.settings.SettingsViewModel
import com.example.ui.screen.timer.FocusModeScreen
import com.example.ui.screen.timer.TimerScreen
import com.example.ui.screen.timer.TimerViewModel
import com.example.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

/** Every route in one place. Arguments are always URL-encoded. */
object Routes {
    /** Welcome / sign-in. Plain "auth" also matches (mode then defaults to sign in). */
    const val AUTH = "auth?mode={mode}"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val RADIO = "radio"
    const val FOCUS_MODE = "battery_saver"      // route id kept for stability
    const val EXAMS = "exams"
    const val APP_BLOCKER = "app_blocker"
    const val CHALLENGES = "challenges"
    const val STYLE = "style"
    const val CAPTURE = "revisions/capture"
    const val CROP = "revisions/crop?path={path}&whole={whole}"
    const val SESSION = "revisions/session?deckId={deckId}"
    const val DECK = "revisions/deck/{deckId}"
    const val NOTE = "revisions/note/{noteId}"

    fun auth(mode: AuthMode) = "auth?mode=${mode.key}"
    /** [whole]: the photo is an already-cropped scan, so start with the whole page selected. */
    fun crop(path: String, whole: Boolean = false) = "revisions/crop?path=${URLEncoder.encode(path, "UTF-8")}&whole=$whole"
    fun session(deckId: String) = "revisions/session?deckId=${URLEncoder.encode(deckId, "UTF-8")}"
    fun deck(deckId: String) = "revisions/deck/${URLEncoder.encode(deckId, "UTF-8")}"
    fun note(noteId: String) = "revisions/note/${URLEncoder.encode(noteId, "UTF-8")}"
}

/** The five top-level destinations, in bottom-bar order. */
enum class TopLevel(
    val key: String,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    FOCUS("focus", R.string.nav_focus, Icons.Rounded.Timer, Icons.Outlined.Timer),
    STUDY("study", R.string.nav_study, Icons.Rounded.Style, Icons.Outlined.Style),
    CHALLENGES("challenges", R.string.nav_challenges, Icons.Rounded.Flag, Icons.Outlined.Flag),
    PROGRESS("progress", R.string.nav_progress, Icons.Rounded.Insights, Icons.Outlined.Insights),
    COMMUNITY("community", R.string.nav_community, Icons.Rounded.Groups, Icons.Outlined.Groups);

    companion object {
        /** Accepts new keys and the legacy notification value "revisions". */
        fun fromKey(key: String?): TopLevel? = when (key) {
            null -> null
            "revisions", "review" -> STUDY
            "challenge", "challenges", "defi", "defis" -> CHALLENGES
            "exams", "stats" -> PROGRESS
            else -> entries.firstOrNull { it.key == key }
        }
    }
}

/** A deep-link request from MainActivity (notification, widget, or lock-screen wake). [token] makes repeats distinct. */
data class NavRequest(
    val tab: TopLevel,
    val progressTab: Int = 0,
    val openFocusMode: Boolean = false,
    val token: Long = System.nanoTime()
)

@Composable
fun AppNavGraph(
    settingsViewModel: SettingsViewModel,
    navRequest: NavRequest? = null,
    onConsumeNavRequest: () -> Unit = {},
    navController: NavHostController = rememberNavController()
) {
    // Activity-scoped view models shared by tabs and their detail screens.
    val timerViewModel: TimerViewModel = viewModel()
    val analyticsViewModel: AnalyticsViewModel = viewModel()
    val examsViewModel: ExamsViewModel = viewModel()
    val revisionsViewModel: RevisionsViewModel = viewModel()
    val challengeViewModel: ChallengeViewModel = viewModel()

    // Keeps the "auto-sync wallpaper with theme" feature working app-wide.
    val forestViewModel: ForestViewModel = viewModel()
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(systemDark) { forestViewModel.updateSystemDarkTheme(systemDark) }

    // Signed in OR guest mode skips the welcome screen. Decided once per launch.
    val authRepository = remember { FocusFlowApplication.instance.authRepository }
    val startDestination = remember { if (authRepository.canEnterApp) Routes.MAIN else Routes.AUTH }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally { it / 6 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 6 } + fadeOut() }
    ) {
        composable(
            route = Routes.AUTH,
            arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = AuthMode.SIGN_IN.key })
        ) {
            // Opened from inside the app (a guest upgrading): back returns there, success too.
            val fromInside = remember { navController.previousBackStackEntry != null }
            AuthScreen(
                onFinished = {
                    if (fromInside) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.MAIN) { popUpTo(Routes.AUTH) { inclusive = true } }
                    }
                },
                onBack = if (fromInside) ({ navController.popBackStack() }) else null
            )
        }

        composable(Routes.MAIN) {
            MainShell(
                navController = navController,
                navRequest = navRequest,
                onConsumeNavRequest = onConsumeNavRequest,
                settingsViewModel = settingsViewModel,
                timerViewModel = timerViewModel,
                analyticsViewModel = analyticsViewModel,
                examsViewModel = examsViewModel,
                revisionsViewModel = revisionsViewModel,
                challengeViewModel = challengeViewModel
            )
        }

        composable(Routes.SETTINGS) {
            SecondaryScreen(title = stringResource(R.string.nav_settings), onBack = { navController.popBackStack() }) { padding ->
                SettingsScreen(viewModel = settingsViewModel, navController = navController, modifier = Modifier.padding(padding))
            }
        }

        composable(Routes.STYLE) {
            val settings by settingsViewModel.state.collectAsState()
            // Opening the picker answers the "choose your style" tip.
            LaunchedEffect(Unit) { settingsViewModel.dismissStyleTip() }
            SecondaryScreen(title = stringResource(R.string.style_screen_title), onBack = { navController.popBackStack() }) { padding ->
                StylePickerScreen(current = settings.appStyle, onSelect = settingsViewModel::updateAppStyle, modifier = Modifier.padding(padding))
            }
        }

        composable(Routes.RADIO) {
            RadioScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.FOCUS_MODE) {
            FocusModeScreen(
                viewModel = timerViewModel,
                onDismiss = {
                    onConsumeNavRequest()
                    if (!navController.popBackStack(Routes.MAIN, inclusive = false)) {
                        navController.popBackStack()
                    }
                },
                onOpenRadio = {
                    onConsumeNavRequest()
                    if (!navController.popBackStack(Routes.MAIN, inclusive = false)) {
                        navController.popBackStack()
                    }
                    navController.navigate(Routes.RADIO)
                }
            )
        }

        composable(Routes.EXAMS) {
            SecondaryScreen(title = stringResource(R.string.progress_tab_exams), onBack = { navController.popBackStack() }) { padding ->
                ExamsScreen(viewModel = examsViewModel, modifier = Modifier.padding(padding))
            }
        }

        composable(Routes.CHALLENGES) {
            SecondaryScreen(title = stringResource(R.string.challenges_title), onBack = { navController.popBackStack() }) { padding ->
                ChallengesScreen(viewModel = challengeViewModel, modifier = Modifier.padding(padding))
            }
        }

        composable(Routes.APP_BLOCKER) {
            AppBlockerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CAPTURE) {
            CaptureScreen(
                viewModel = revisionsViewModel,
                onImageCaptured = { path ->
                    navController.navigate(Routes.crop(path)) { popUpTo(Routes.CAPTURE) { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CROP,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType; defaultValue = "" },
                navArgument("whole") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            CropEditorScreen(
                imagePath = URLDecoder.decode(entry.arguments?.getString("path").orEmpty(), "UTF-8"),
                wholePage = entry.arguments?.getBoolean("whole") ?: false,
                viewModel = revisionsViewModel,
                onCropConfirmed = { navController.popBackStack(Routes.MAIN, inclusive = false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.SESSION,
            arguments = listOf(navArgument("deckId") { type = NavType.StringType; defaultValue = "ALL" })
        ) { entry ->
            RevisionSessionScreen(
                deckId = URLDecoder.decode(entry.arguments?.getString("deckId") ?: "ALL", "UTF-8"),
                viewModel = revisionsViewModel,
                onFinishSession = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DECK,
            arguments = listOf(navArgument("deckId") { type = NavType.StringType })
        ) { entry ->
            RevisionDeckDetailScreen(
                deckId = URLDecoder.decode(entry.arguments?.getString("deckId").orEmpty(), "UTF-8"),
                viewModel = revisionsViewModel,
                onNoteClick = { navController.navigate(Routes.note(it)) },
                onStartReview = { navController.navigate(Routes.session(it)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.NOTE,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { entry ->
            RevisionNoteDetailScreen(
                noteId = URLDecoder.decode(entry.arguments?.getString("noteId").orEmpty(), "UTF-8"),
                viewModel = revisionsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** Top-level shell: app bar + adaptive navigation (bar on phones, rail on wide screens) + tab pager. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainShell(
    navController: NavHostController,
    navRequest: NavRequest?,
    onConsumeNavRequest: () -> Unit,
    settingsViewModel: SettingsViewModel,
    timerViewModel: TimerViewModel,
    analyticsViewModel: AnalyticsViewModel,
    examsViewModel: ExamsViewModel,
    revisionsViewModel: RevisionsViewModel,
    challengeViewModel: ChallengeViewModel
) {
    val settings by settingsViewModel.state.collectAsState()
    val tabs = TopLevel.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var progressTab by rememberSaveable { mutableIntStateOf(0) }
    val current = tabs[pagerState.currentPage]
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(navRequest) {
        val req = navRequest ?: return@LaunchedEffect
        onConsumeNavRequest()
        progressTab = req.progressTab
        pagerState.scrollToPage(req.tab.ordinal)
        if (req.openFocusMode && navController.currentDestination?.route != Routes.FOCUS_MODE) {
            navController.navigate(Routes.FOCUS_MODE) { launchSingleTop = true }
        }
    }

    fun select(tab: TopLevel) {
        scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
    }

    Row(Modifier.fillMaxSize()) {
        if (wide) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = tab == current,
                        onClick = { select(tab) },
                        icon = { Icon(if (tab == current) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) }
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (!(isImeVisible && current == TopLevel.CHALLENGES)) {
                    androidx.compose.material3.TopAppBar(
                        title = { Text(stringResource(current.label)) },
                        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        actions = {
                            IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings))
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!wide && !isImeVisible) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == current,
                                onClick = { select(tab) },
                                icon = { Icon(if (tab == current) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            val bottomPadding = if (isImeVisible) 0.dp else padding.calculateBottomPadding()
            val topPadding = if (isImeVisible && current == TopLevel.CHALLENGES) 0.dp else padding.calculateTopPadding()
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = settings.swipeToNavigate && !isImeVisible,
                beyondViewportPageCount = 0,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
            ) { page ->
                if (tabs[page] == TopLevel.FOCUS) {
                    TimerScreen(
                        viewModel = timerViewModel,
                        settingsViewModel = settingsViewModel,
                        topBarPadding = padding.calculateTopPadding(),
                        onNavigateToBatterySaver = {
                            if (navController.currentDestination?.route != Routes.FOCUS_MODE) {
                                navController.navigate(Routes.FOCUS_MODE) { launchSingleTop = true }
                            }
                        },
                        onNavigateToRadio = { navController.navigate(Routes.RADIO) },
                        onNavigateToChallenges = { select(TopLevel.CHALLENGES) },
                        onNavigateToStyles = { navController.navigate(Routes.STYLE) }
                    )
                } else {
                    // Tablets, foldables and landscape: keep each tab a readable column instead of stretching it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = topPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Box(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize()) {
                            when (tabs[page]) {
                                TopLevel.FOCUS -> Unit
                                TopLevel.STUDY -> RevisionsHomeScreen(
                                    viewModel = revisionsViewModel,
                                    onAddClick = { navController.navigate(Routes.CAPTURE) },
                                    onImageCaptured = { navController.navigate(Routes.crop(it)) },
                                    onPageScanned = { navController.navigate(Routes.crop(it, whole = true)) },
                                    onDeckClick = { navController.navigate(Routes.deck(it)) },
                                    onNoteClick = { navController.navigate(Routes.note(it)) },
                                    onStartSessionClick = { navController.navigate(Routes.session(it)) }
                                )
                                TopLevel.CHALLENGES -> ChallengesScreen(viewModel = challengeViewModel)
                                TopLevel.PROGRESS -> ProgressTab(
                                    selected = progressTab,
                                    onSelect = { progressTab = it },
                                    analyticsViewModel = analyticsViewModel,
                                    examsViewModel = examsViewModel
                                )
                                TopLevel.COMMUNITY -> CommunityTab(navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressTab(
    selected: Int,
    onSelect: (Int) -> Unit,
    analyticsViewModel: AnalyticsViewModel,
    examsViewModel: ExamsViewModel
) {
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = selected,
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) {
            Tab(selected = selected == 0, onClick = { onSelect(0) }, text = { Text(stringResource(R.string.progress_tab_stats)) })
            Tab(selected = selected == 1, onClick = { onSelect(1) }, text = { Text(stringResource(R.string.progress_tab_exams)) })
        }
        if (selected == 0) AnalyticsScreen(viewModel = analyticsViewModel)
        else ExamsScreen(viewModel = examsViewModel)
    }
}

@Composable
private fun CommunityTab(navController: NavHostController) {
    val account by FocusFlowApplication.instance.authRepository.state.collectAsStateWithLifecycle()
    if (account !is AccountState.SignedIn) {
        GuestCommunity(
            onCreateAccount = { navController.navigate(Routes.auth(AuthMode.CREATE_ACCOUNT)) },
            onSignIn = { navController.navigate(Routes.auth(AuthMode.SIGN_IN)) }
        )
        return
    }
    val owner = LocalViewModelStoreOwner.current
    // Firebase can be unavailable (no network on first launch, misconfiguration): degrade gracefully.
    val communityViewModel = remember(owner) {
        runCatching { owner?.let { ViewModelProvider(it)[CommunityViewModel::class.java] } }.getOrNull()
    }
    if (communityViewModel != null) {
        CommunityScreen(viewModel = communityViewModel)
    } else {
        EmptyState(
            icon = Icons.Rounded.CloudOff,
            title = stringResource(R.string.community_unavailable_title),
            body = stringResource(R.string.community_unavailable_body)
        )
    }
}

/** Guests (no account) see what Community offers and how to join. Their local data comes along. */
@Composable
private fun GuestCommunity(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyState(
            icon = Icons.Rounded.EmojiEvents,
            title = stringResource(R.string.community_guest_title),
            body = stringResource(R.string.community_guest_body),
            actionLabel = stringResource(R.string.community_guest_create),
            onAction = onCreateAccount
        )
        TextButton(onClick = onSignIn, modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(stringResource(R.string.community_guest_sign_in))
        }
    }
}

/** Secondary destination with a back arrow (predictive back is handled by Navigation). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        content = content
    )
}
