# Focus Island redesign: handoff summary (Phases 1 to 5 complete + 2.1.0 island + 2.2.0 features)

Use this file to continue in a new chat: attach it plus the repomix file and the latest
FocusIsland_all_changes.zip (cumulative), or a fresh repomix of the updated repo.

## Design direction: "Lagoon at dusk"
Calm teal (focus) + warm sand (rewards) + soft coral (stop); flat tonal Material 3 surfaces, no glass/neumorphism.
Light: primary #1F6A64, primaryContainer #A7F2E9, secondary #7A5900, secondaryContainer #FFDEA3, tertiary #9C4234,
background/surface #F6FAF8, onSurface #171D1C, onSurfaceVariant #3F4947, outlineVariant #BEC9C6.
Dark: primary #8BD5CD, primaryContainer #00504B, secondary #F6BE48, tertiary #FFB4A7, background/surface #0F1514,
onSurface #DEE4E2, onSurfaceVariant #BEC9C6.
Type: Plus Jakarta Sans (display/headline/title), system font (body/label); TimerDisplayStyle 88sp tabular.
Shapes: 4/8/16 (cards)/24 (sheets, dialogs)/32. Spacing (ui/theme/Dimens.kt): 4/8/12/16/24/32/48,
minTouchTarget 48, formMaxWidth 440, buttonHeight 52, illustration 96, contentMaxWidth 640.
Design-system components (ui/designsystem/Components.kt): ThemePreviews, SectionHeader, EmptyState, StatTile,
InfoBanner(BannerTone), ConfirmDialog, LoadingState, PhaseRing.
Content tokens: ui/theme/NotePalette.kt (HighlightTone + NotePalette.heading/highlight: one palette for reader,
review and editor; stored colour names unchanged), CameraPalette in Color.kt (camera/crop always dark; CameraTheme
in CaptureScreen.kt forces LagoonDarkColors for M3 components there). RevisionHeadingColors was removed.

## Decisions made
- Defaults A1-A9 approved: guest mode (anonymous auth), granular reset, no fake sessions, practice-ahead never
  reschedules, auto Focus-mode and ambient rotation become settings (default off), radio sharing opt-in, unused
  permissions removed, English base strings + French translation in Phase 4.
- Points = 1 per completed focus minute everywhere (leaderboard points x5 for existing users).
- Hilt and DataStore deferred: manual DI in FocusFlowApplication and SharedPreferences "focusflow_prefs" (same keys).
  Type-safe nav replaced by central string `Routes`.
- Account: AuthRepository (data/auth) is the single source of truth: AccountState = SignedOut | Guest | SignedIn.
  Guest = local pref "guest_mode" (works offline) + best-effort anonymous Firebase sign-in in the background.
  Creating an account as an anonymous guest links the email credential (same uid). After any real sign-in:
  session sync + RevisionSyncWorker.scheduleSyncWork. signOut() also clears guest mode.
- Routes.AUTH = "auth?mode={mode}" (plain "auth" still matches). Start destination = MAIN if signed in OR guest.
  Opened from inside the app (guest upgrade) -> back arrow, guest button hidden, success pops back.
- Community tab: non-signed-in users see a "Join the community" CTA (create account / sign in).
- Community: leaderboard = `leaderboard` collection ordered server-side by points | totalMinutes | treeCount |
  currentStreak (top 100). Old fake "Weekly"/"Monthly" sorts (they showed all-time points) were dropped.
  Friend accept/remove are single Firestore batches. Messages via Channel → snackbar.
- Radio "listening now" sharing is opt-in: pref "share_listening" (default false), checked in RadioPlayerService.
- Email is no longer written to users/{uid} (readable by members); SessionRepository sync deletes the field.
- firestore.rules (repo root) is the source of truth for security rules; the user publishes it in the console.
- CI restores root Gradle files + gradle/libs.versions.toml from actions-backup/: only app/build.gradle.kts may change.

## Building inside Claude (works now)
- Gradle 9.4.0 from services.gradle.org, Android cmdline-tools from dl.google.com, then
  `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`, JDK 17 via apt
  (`openjdk-17-jdk-headless`; remove the nodesource apt source first). The preinstalled Java 21 has no javac.
- Build dir = repo with actions-backup root files copied in, local.properties sdk.dir, a generated debug.keystore,
  (no binary placeholder needed any more).
- Tool calls are cut after about 5 minutes and plain background jobs die between calls: launch Gradle with
  `setsid nohup script.sh > /dev/null 2>&1 < /dev/null &` and poll the log.
- Rebuild the repo by parsing repomix ("## File: path" + fenced block), apply the zip, delete the two obsolete files.
  apt rsync is needed by the sync script. Gradle daemons do not survive between replies: relaunch the build.
- Maven Central intermittently answers 429 to the sandbox IP: run Gradle with --max-workers=2
  -Dorg.gradle.internal.repository.max.retries=12 -Dorg.gradle.internal.repository.initial.backoff=3000 and
  re-run in a loop until downloads are cached (only "^e: " lines are real compile errors). The Google Central
  mirror is blocked by egress policy; do not use it. The repomix is read with the Projects tool (project_read).
- No screen uses the legacy neumorphic/glass components any more (deleted in 3g).
- Last result: see the 2.1.0 section (build results updated there).

## Completed (all in the cumulative FocusIsland_all_changes.zip)
- Phase 5 (release): versionCode 8 / versionName 2.0.0; release build shrunk with R8 + resource shrinking
  (app/proguard-rules.pro: FriendRequest kept for Firestore toObject, Retrofit API, Moshi codegen model,
  enums; res/raw/keep.xml keeps drawable ic_radio_default used by name). Unused dependencies removed
  (firebase-config, firebase-storage, firebase-ai, fragment-ktx, media3-ui, media3-datasource-okhttp,
  logging-interceptor). CI (.github/workflows/build-and-sign.yml, copies in app/ and actions-backup/app/):
  unit tests -> assembleRelease + bundleRelease -> sign APK and AAB -> upload APK, AAB, mapping.txt;
  PRs run tests only. actions-backup/app/build.gradle.kts now mirrors app/build.gradle.kts.
  RELEASE.md: publishing steps, Play data-safety table, sensitive-permission justifications.
  Known: AGP 8.5's R8 warns it can't parse Kotlin 2.2 metadata (harmless here: no kotlin-reflect /
  KotlinJsonAdapterFactory). Next upgrade: AGP >= 8.11 in actions-backup/gradle/libs.versions.toml.
- Phase 4 / 4b: predictive back (enableOnBackInvokedCallback), tabs and deck list centred at contentMaxWidth
  on tablets / landscape (wrapper in MainShell pager), Focus ring capped to 55 % of screen height,
  CappedFontScale (designsystem) caps the 88 sp timer at 130 % text size, Settings rows role=Button,
  autofill on sign-in fields (ui/components/Autofill.kt, experimental Compose 1.7 API, swap for
  contentType when Compose >= 1.8), ContrastTest (theme + highlight pairs >= 4.5:1).
- CityBackground removed: CityBackground.kt, BuildingBackgroundRenderer.kt (+ its test), assets/window-map.json,
  drawable/cityscape_windows_transparent.png, unused ForestBackground/ForestBackgroundContent composables and
  ForestTreeRenderer.drawAtmosphericParticles. The build no longer needs any binary placeholder.
- Phase 4 / Batch 4a: res/values-fr/strings.xml (every translatable key: strings, plurals with one/many/other,
  string-array; French typography with no-break spaces before : ? ! % and inside « »), res/xml/locales_config.xml
  + android:localeConfig (Android 13+ per-app language). Leftover hardcoded texts moved to resources: backup,
  recording and review-reminder notifications and channels, timer media title for ambient sounds, wallpaper
  errors, default card title / "no text found" (ML Kit default title was French-only), restored-card title.
  Fix: saving a voice card failed silently -> error text in the form. RULE FROM NOW ON: every new English
  string needs its French twin in values-fr (generator kept outside the repo; edit values-fr directly).
- Phase 3 / Batch 3g Focus mode: TimerScreen.kt (2,559 lines) replaced by ui/screen/timer/FocusModeScreen.kt
  (FocusModeScreen(viewModel, onDismiss, onOpenRadio)): true-black FocusModePalette, Lagoon dark scheme, keep
  screen on, immersive bars, brightness strip (restored on exit), FULL_SENSOR orientation + rotate toggle
  (restored on exit), idle dim after 10 s, battery % + battery-saver badge, PhaseRing with phase colours
  (focus primary, short break extendedColors.rest, long break secondary), today line, sound chip -> the Focus
  tab's SoundsSheet (now internal in FocusScreen.kt with phaseLabel/ambientLabel/formatClock/CONFIRM_END_AFTER_MS),
  Stop with the same confirmation as the Focus tab, play/pause resumes a paused session (old code restarted it),
  gestures double-tap exit / long-press play-pause / swipe-up Today sheet (milestone progress, totals, time of
  day, session log), TalkBack custom actions for exit and Today, milestone flash + haptic at 25/50/75 %.
  Deleted TimerScreen.kt, NeumorphicComponents.kt, GlassCard.kt, GlassButton.kt, NeumorphicColors.kt.
  Favourite toggling from Focus mode moved to the Radio screen (Browse all stations). Test FocusModeTest.
- Phase 3 / Batch 3f: AppBlockerScreen(onBack) rewritten (access card with Allow buttons re-checked on resume,
  warning banner when a block can't work, search, per-app FilterChips Notifications / Opening, blocked first,
  loading + empty states); AppBlockerViewModel icons pre-rendered to 96 px ImageBitmaps off the main thread,
  installedApps null while loading. FocusOverlayActivity: M3 calm screen, "Back to focus" opens MainActivity on
  the Focus tab (EXTRA_TARGET_SCREEN), system back does the same (used to reveal the blocked app), onNewIntent
  updates the app name, nudges in string-array, excludeFromRecents. Widgets: WidgetText (daysUntil via
  java.time, localized labels, openExams PendingIntent -> Progress > Exams); countdown uses
  StatsCalculator.totals (points = minutes, same streak as Stats); matrix month names from the locale; layout
  sample texts moved to tools:text. TimerScreen.kt: NeumorphicColors replaced by colorScheme.
  Deleted ui/components/ForestScaffold.kt (unused). New test WidgetTextTest.
- Phase 3 / Batch 3e Settings: SettingsScreen rewritten as one scrolling page of grouped cards (no accordions):
  Account (signed in: name/email, Sign out with confirmation; guest: Create account / Sign in rows; Delete account
  or "Erase all data" for guests), Focus timer (steppers with clamped ranges in SettingsState, DND switch that
  opens policy access when missing, re-checked on resume, vibrate, auto Focus mode, ambient rotation 0 = Off,
  App blocker link), Study and AI (new cards/day, reminder, Gemini key dialog with show/hide + AI Studio link,
  models dialog with select/test/add/remove, full-screen prompt editor; saving the default clears the override,
  Exams link), Appearance and privacy (theme System/Light/Dark, dynamic colour on Android 12+, swipe tabs,
  "share what I'm listening to"), Wallpaper, Home screen widgets (pin), Backup and data (export/import dialogs,
  BackupStatusCard, reset with confirmation), About (version, developer contact). New files SettingsComponents.kt
  (SettingsGroup, SettingsRow, SwitchRow, StepperRow, SettingsDivider) and SettingsDialogs.kt. Snackbars replace
  Toasts. SettingsViewModel: ranges + clamping, shareListening (same pref key, clears Firestore currentRadio when
  off), deleteAccount also calls authRepository.signOut(). CommunityViewModel listens to the share_listening pref.
- Phase 3 / Batch 3a-2 Radio: RadioScreen rewritten (TopAppBar + Add station, tabs My stations / Discover,
  filter chips Favourites / All / categories / New category, delete custom category with confirmation, remove
  custom station with Undo snackbar, Discover search (debounced 400 ms, cancels stale requests) + Popular,
  directory-down banner with retry showing built-in stations, Save to category, pinned NowPlayingBar with
  status Live / Connecting / Paused / Error + prev/next through the list the station was started from).
  RadioViewModel: PlaybackStatus (onPlayerError), DiscoverUiState, skip(), restoreStation(), duplicate seeding
  removed (DatabaseSeeder owns it), favouriting no longer overwrites built-in rows, artwork URI uses
  context.packageName (was "com.focusflow"), Discover hides plain-http streams, Add station requires https.
  RadioDao: getStation(id), getBuiltInStations(). Built-in category names localized by id (STUDY, GLOBAL).
  Route RADIO no longer wrapped in ForestScaffold; RadioScreen(onBack).
- Phase 3 / Batch 3b-3: CaptureScreen (camera only: flash, gallery, permission denied/blocked, camera error,
  haptic shutter; typed/voice cards live in the Study "New card" sheet), CropEditorScreen (off-main-thread
  downsampled decode via ImageTools, saveable selection/mode/rotation, Rectangle / Free shape / Page modes, Page
  mode straightens with perspective correction, CreateCardSheet: DeckPicker + On device / Gemini / Keep as photo,
  explain mode), ImageTools.kt (load/rotate/crop, copyUriToCache, purgeStaleCaptures), RevisionsViewModel
  BackgroundOcrTask.kind = OcrTaskKind (no hardcoded French; statusMessage/engineName removed), StudyComponents task
  card, CardInlineEditor = full-screen editor (own top bar, Save, discard confirmation, BackHandler, rememberSaveable
  savers, segmented Edit/Preview, M3 formatting toolbar), FullScreenNoteReaderDialog reuses CardContent
  (text size control + pinch, tap photo to zoom), NoteBlocksRenderer/HighlightedMarkdownWithTables restyled
  (headings announced as headings, accent bars follow wrapped text). Note screen renders the editor full screen.
- Phase 1 (3 batches): DB v8 (no destructive fallback, schema export, indices, legacy JSON repair), DatabaseSeeder,
  StatsCalculator (+test), honest two-way session sync, safe backup restore, font scaling respected, drift-free
  persisted timer with in-service app blocker (AppLaunchMonitor), safe DND, review session snapshot queue + undo +
  practice ahead, granular reset, full account deletion, AAC voice cards, selected Gemini model used,
  permissions/FileProvider tightened.
- Phase 2 / Batch 1: Lagoon theme + tokens + components + previews, 4-tab adaptive nav shell, Settings route,
  splash screen, deep links focusisland://app/<tab>.
- Phase 2 / Batch 2: SecretStore (Keystore AES-GCM) for the Gemini key, header auth, full card cloud sync,
  backup exclusions; AuthRepository + new AuthViewModel/AuthScreen (sign in, create account, reset dialog,
  guest mode), start destination rule, guest Community CTA, guest-safe sign out/delete, AuthValidationTest.
  Fix: Type.kt @OptIn(ExperimentalTextApi::class).
- Phase 3 / Batch 3b-2: RevisionNoteDetailScreen (CardContent shared composable, ScheduleSummary,
  MoveToDeckDialog, BackHandler while editing), RevisionSessionScreen (FlipCard, GradeButtons with
  Sm2Scheduler interval preview, SessionDone), StudyMedia.kt (Coil CardImage, ZoomableImageDialog,
  AudioCardPlayerView, MediaActions, save/share helpers returning Boolean). Session/note/deck routes no longer
  wrapped in ForestScaffold. Legacy formatDueIn removed.
- Phase 3 / Batch 3b-1: Study home (RevisionsHomeScreen: TodayCard, DeckRow, search, AddCardSheet),
  RevisionDeckDetailScreen (sort, edit deck via VM.updateDeck, delete, ALL_DECKS), CardCreationDialogs.kt
  (full-screen typed + voice card forms), StudyComponents.kt (dueLabel plurals, CardRow, BackgroundOcrTasksSection,
  DeckPicker, DeckDialog, deckColor/contentOn), DeckPalette tokens. Legacy formatDueIn kept for the card detail.
- Phase 3 / Batch 3d: Community redesign (Leaderboard + Friends tabs, skyline with SkylinePalette tokens,
  add-friends sheet, friend sheet + remove confirmation), CommunityViewModel rewrite, hardened firestore.rules
  (adds revision_notes rule, members-only profiles, friends-array and friend-request validation, leaderboard
  stat validation), opt-in listening share, email removed from public profile.
- Phase 3 / Batch 3a: Focus tab (FocusScreen.kt), Sounds sheet, Focus mode brightness restore + Exit, opt-in auto Focus mode.
- Phase 3 / Batch 3c: Progress tab: Stats and Exams.

## 2.1.0 island update (after Phase 5, user feedback)
- Timer: `RingClock` (designsystem/Components.kt) sizes the digits from the ring (BoxWithConstraints, 0.2 × diameter
  for MM:SS); replaces `CappedFontScale` (removed). Used in FocusScreen and FocusModeScreen.
- Island concept: `ui/components/IslandRenderer.kt` (DrawScope, off-screen safe) + `IslandColors`/`IslandPalette.Day/Night`
  in Color.kt (replaces SkylinePalette). Palm per session up to 9, island grows to full size at 40 sessions,
  hut at 12, lighthouse at 25 (beam + glow at night). `ForestTreeRenderer.kt` deleted.
- WallpaperHelper rewritten: `drawScene` (live wallpaper draws straight on the surface with the real session count;
  it used 0 before), `renderBitmap`, suspend `setIslandWallpaper` (off main thread; Settings "Apply now" used to
  render on the main thread). ForestViewModel re-applies the wallpaper when the session count changes (auto update on).
  Settings wallpaper group shows a preview + `wallpaper_preview_body`.
- Community: `ArchipelagoCard` + `IslandThumb` replace SkylineCard/BuildingThumb; strings `community_archipelago_*`
  replace `community_skyline_*`; tokens `Spacing.sceneHeight`, `islandThumb`, `wallpaperPreviewHeight`.
- Leaderboard: ranked from `users` (orderBy metric, top 100) merged with me + friends (`mergeRanking`, tested by
  LeaderboardMergeTest). The `leaderboard` collection is still written (compatibility) but no longer read: members
  whose app never wrote it were missing. Members with no stat fields and not friends still don't appear until they sync.
- Signing: new upload key created 2026-10-13 (alias `upload`, PKCS12, RSA 4096, CN=Focus Island, C=DZ,
  same store/key password); the user replaces the 4 GitHub secrets. App was never published, so no Play impact.
- Version 2.1.0 (versionCode 9).
- Last result (2026-10-13): assembleDebug + 16 unit tests + assembleRelease + bundleRelease (R8) OK for 2.1.0.
  Scene checked visually through a canvas port of IslandRenderer (wallpaper day/night, archipelago, thumb).

## 2.2.0 features ported from the user's other app (versionCode 10)
- Challenges: `data/challenge/ChallengeStore.kt` (own prefs file "challenges", JSON; one active + history ≤ 200),
  `service/ChallengeAlarmReceiver.kt` (ongoing chronometer notification with "Goal reached" action, deadline alarm:
  exact if allowed else setAndAllowWhileIdle, re-armed on BOOT_COMPLETED/MY_PACKAGE_REPLACED; channel "challenges"),
  `ui/screen/challenge/ChallengeViewModel.kt` (+ pure `ChallengeTime`, tested) and `ChallengesScreen.kt`
  (new-challenge sheet: goal, Duration chips/hours+minutes or End time via M3 TimePicker; 5-s countdown with
  ToneGenerator beeps; active ring; result card; history + success rate; clear). Route `challenges`
  (SecondaryScreen), entry card on the Focus tab. `designsystem/Confetti.kt` (skipped when animations are off).
- Focus tab: `DayDial.kt` 24-h dial (blocks from `dayBlocks()` in TimerViewModel, tested; live arc; now marker),
  cycle dots, `SoundCardView` (radio/ambient/none, last used via `data/LastSound.kt`; RadioViewModel restores the
  last station and stops ambient when radio plays), `ChallengeEntry`.
- Scanner: `play-services-mlkit-document-scanner:16.0.0` (SCANNER_MODE_FULL, 1 page, JPEG, gallery import) from the
  Study add sheet → crop editor with the whole page selected (`Routes.crop(path, whole = true)`); falls back to the camera.
- Gemini: `data/repository/GeminiModels.kt` (ListModels API, cache 12 h in prefs, `rank`, `attemptOrder`, tested);
  choices `auto:flash|auto:flash-lite|auto:pro` or an id; old default `gemini-2.5-flash` migrated once to `auto:flash`.
  GeminiOcrEngine tries the next model on any non-key error. Key dialog "Test"; models dialog lists Google's models,
  refresh, test, custom ids.
- Verified: assembleDebug, 24 unit tests, assembleRelease + bundleRelease (R8). Screens rendered locally with
  Robolectric sdk 34 + Roborazzi (harness not shipped).

## 2.2.1 fixes (versionCode 11)
- FocusContent: no vertical scroll. BoxWithConstraints: portrait Column(banner, TimerDial weight 1f, dots, controls,
  FocusExtras); landscape Row(TimerDial | scrollable column). TimerDial sizes to min(width, height, focusDialMax 360dp),
  drops the day dial below focusDialMin 220dp. FocusExtras = segmented Sound / Challenge / Today + one panel
  (heightIn focusPanelHeight), Challenge preselected when one runs.
- TimerScreen sets view.keepScreenOn while running; auto Focus mode default true (pref "auto_focus_mode").
- FocusModeScreen: idle after 8 s → chrome alpha 0, clock 0.75, burn-in IntOffset shift ±12 px each minute,
  tap-catching overlay while idle (double-tap/long-press kept), never idle with TalkBack; ring sized by
  BoxWithConstraints; primaryContainer play button; BrightnessStrip pill + LightMode icon; sound_choose.
- Cards: appendFormattedMarkdown no DOT_MATCHES_ALL, flanking single * / _; parseNoteLines treats "_ x" / "_x" (no
  closing _) as bullets and a lone " _ " as "–"; NativeTableRenderer = full-width weighted columns
  (`columnWeights`, floor 80 % of equal share), RichTextBlockRenderer per cell, vertical dividers, scroll only when
  columns < tableMinColumnWidth; table highlights from text blocks; "<br>" in pipe tables → line break.
  VERBATIM_PROMPT: list/underline/cell/box-layout rules. Tests: CardFormattingTest.

## 2.2.2 community + widgets (versionCode 12)
- CommunityScreen `ArchipelagoCard` (internal): world coords from `archipelagoLayout(n)` (rings of 6k, y ×0.62,
  tested), camera `zoom/camera` state (null = fit all, 0.3–1.4), detectTransformGestures pan/pinch, tap hit-test,
  culling, `drawSeaMarks` grid moving with camera, recenter FilledTonalIconButton. Height `Spacing.archipelagoHeight`.
- Widgets: fixed "Lagoon" palette `values[-night]/widget_palette.xml` (v31 dynamic overrides deleted),
  `color/wgt_*` selectors, root id `@android:id/background`. Countdown `buildViews(context, exams, totals, compact)`
  (compact < 150 dp hides stats title and 3rd exam). Matrix `generateMatrixBitmap(context, w, h, ...)` at the widget
  size from options (≤ 2×, ≤ 700 000 px), `onAppWidgetOptionsChanged`. `WidgetRefreshScheduler.refreshNow` sends
  EXTRA_APPWIDGET_IDS (was missing). Receivers listen to BOOT/PACKAGE_REPLACED/TIME/TIMEZONE/LOCALE. Widget info:
  previewImage PNGs (drawable-nodpi, rendered from the real layouts), updatePeriodMillis 1 h.
- RemoteViews rule: no plain `<View>` in widget layouts (use FrameLayout).

## 2.3.0 styles (versionCode 13)
- `ui/theme/AppStyle.kt` (enum + pref keys `app_style`, `app_style_tip_seen`), `FocusStyle.kt` (colours light/dark,
  extended, island day/night, typography, timerText, shapes, CelebrationKind, success sound, plantedMessage, kidCopy;
  `FocusStyles.of`, `LocalFocusStyle`, `styledString` for kid wording), `StyleColors.kt` (Arcade/Blossom/Explorer
  schemes + `StyleIslandPalettes`), Type.kt `styleTypography`/`timerStyle` (`TimerDisplayStyle` = composable getter),
  Shape.kt per style (Arcade = CutCornerShape).
- Theme.kt: `FocusIslandTheme(..., style)`; dynamic colour only for Lagoon; colours animate 450 ms.
- IslandColors gained `kind` (PALM/NEON/SAKURA/TREASURE), `accent`, `accent2`, `ink` (defaults = Lagoon).
  IslandRenderer: neon palms/server/radio tower/grid; cherry trees/tea house/lantern/petals; treasure chest
  (opens at 25)/pirate flag/turtle with ink outlines.
- `designsystem/Celebration.kt` replaces Confetti.kt (CONFETTI, PIXELS + LEVEL UP, PETALS + hearts, STARS + balloons),
  `playSuccessSound` (res/raw/success_*.ogg, synthesized). Used by Challenges and the Focus tab "planted" moment.
- Fonts: res/font chakra_petch_*, quicksand_*, fredoka_* (OFL, digits equal-width, renamed inside the files:
  Focus Island Tech/Soft/Play; licenses/OFL-fonts.txt).
- StylePickerScreen (route `style`), Settings row; Focus tab tip (InfoBanner gained onDismiss).
- Widgets: scripts/gen_widget_styles.py generates per-style layouts/colours; `widget/theme/WidgetStyles.kt` maps
  style → layout and colour ids; WidgetTheme.resolve(context, style). Lagoon widget accent_soft #2A8279 (3:1).
- Tests: StyleContrastTest (all styles AA), WidgetStyleContrastTest (widget palettes over white/black).
- Launcher icon per style: scripts/gen_launcher_icons.py generates drawable/ic_launcher_{background,foreground,monochrome}[_style]
  and mipmap-anydpi-v26/ic_launcher[_round][_style] (scene scaled 0.78 into the visible 72 dp). Manifest: MainActivity has no
  LAUNCHER filter; activity-aliases .LauncherLagoon (enabled) / Arcade / Blossom / Explorer (disabled). `ui/theme/AppIcon.apply`
  enables the chosen alias then disables the others, called from MainActivity.onStop. Splash keeps the Lagoon foreground.
- Known limit: M3 buttons stay pill-shaped in every style (no theme hook); cards, sheets, dialogs, fields follow.

## Remaining
Nothing planned. Optional later: AGP/Compose upgrade (autofill contentType, R8 Kotlin 2.2 metadata),
reminder time picker in Settings (currently fixed 19:00), instrumented UI tests.

## Working method
- Source of truth = repomix-output.md with the cumulative zip applied on top.
- Each batch: zip of complete files mirroring repo paths (cumulative) + AI Studio prompt .md; compile here before delivering.
