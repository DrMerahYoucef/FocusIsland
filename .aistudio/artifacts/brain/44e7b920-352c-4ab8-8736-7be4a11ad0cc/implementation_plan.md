# Fix Failing `MainActivityStartupTest` on Line 38 (`"New challenge"`)

## Root Cause Analysis
When `MainActivityStartupTest.fullAppFlowAndTabsAndSettingsAndDialogsDoNotCrash` clicks `"Challenges"` (line 34) and then attempts to click `"New challenge"` (line 38), it fails to find the `"New challenge"` node due to three interacting factors:

1. **Mid-Scroll `Scaffold` `TopAppBar` Unmounting & Pager Transition (`AppNavGraph.kt`)**:
   - In `MainShell`, `Scaffold(topBar = ...)` conditionally renders `TopAppBar` only when `current == TopLevel.FOCUS`.
   - When tapping `"Challenges"` (page 2) from `"Focus"` (page 0), `select(tab)` calls `pagerState.animateScrollToPage(2)` while `beyondViewportPageCount = 0`. Halfway through the scroll (at page 1, `"Study"`), `current == TopLevel.FOCUS` flips to `false`, causing `Scaffold` to unmount its `TopAppBar` and change `padding.calculateTopPadding()` mid-animation.
   - Switching `select(tab)` to `pagerState.scrollToPage(tab.ordinal)` (matching `navRequest` tab navigation) and keeping `MainShell`'s `Scaffold` layout stable ensures clicking any bottom/rail navigation tab lands deterministically on the target tab on the first frame after `waitForIdle()`.

2. **Shared Persistent `ChallengeStore` State Across Robolectric Tests (`StudyGroupChallengeTest.kt` & `MainActivityStartupTest.kt`)**:
   - `StudyGroupChallengeTest` mutates `ChallengeStore(context)` (which persists to `SharedPreferences("challenges")`) and `FocusFlowApplication.instance.challengeStore`.
   - If any test leaves an active or recent challenge state in `SharedPreferences`, `PersonalChallengeTabContent` renders `ActiveChallengeCard` or `ResultCard` instead of the `"New challenge"` button (`personal_challenge_new_button`).

3. **Test Isolation & Viewport Scrolling (`MainActivityStartupTest.kt`)**:
   - Resetting `ChallengeStore` and ensuring `AppLanguage.ENGLISH` at the start of `MainActivityStartupTest`, ensuring the Personal tab (`challenges_tab_personal`) is active, and scrolling to `"New challenge"` before clicking makes the UI test deterministic across all screen sizes and CI test orderings.

---

## Proposed Changes

1. **`app/src/main/java/com/example/ui/navigation/AppNavGraph.kt`**
   - Update `select(tab: TopLevel)` in `MainShell` to use `pagerState.scrollToPage(tab.ordinal)` so tapping bottom navigation or navigation rail items switches directly to the target tab without intermediate page compositions or mid-scroll `TopAppBar` layout shifts.

2. **`app/src/test/java/com/example/StudyGroupChallengeTest.kt`**
   - Clean up `ChallengeStore` (`SharedPreferences("challenges")`) at the end of the `ChallengeStore` persistence test so it never leaks state to other tests running in the same Gradle worker.

3. **`app/src/test/java/com/example/MainActivityStartupTest.kt`**
   - Reset `ChallengeStore` state and ensure `AppLanguage.ENGLISH` before running the startup flow.
   - Align `@Config(application = FocusFlowApplication::class)` with the project's default Robolectric SDK so tests run consistently in both local and GitHub Actions environments.
   - Ensure the `"New challenge"` button is scrolled into view before clicking.
