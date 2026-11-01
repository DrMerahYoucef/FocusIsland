# Unified Compact Pill Tab Headers & Focus-Only Settings Button

Apply the compact segmented pill tab bar design to **Progress** and **Community** (matching **Challenges**), remove the top bar screen title text across all five main screens, and keep the **Settings** button exclusively on the **Focus** screen.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Confirmed Decision 1 (Compact Pill Tabs on Multi-Tab Screens)**: Replace the `PrimaryTabRow` on **Progress** and **Community** with the sleek compact segmented pill tab bar used on **Challenges**.
> - **Confirmed Decision 2 (Remove Top Bar Screen Titles)**: Remove the redundant screen title text (`Focus`, `Study`, `Challenges`, `Progress`, `Community`) from the top bar across all main screens.
> - **Confirmed Decision 3 (Settings Button Only on Focus Screen)**: Show the **Settings** icon button exclusively on the **Focus** screen and remove it from **Study**, **Challenges**, **Progress**, and **Community**.

## 1. Overview & Core Concept

- **What It Does**:
  - **Focus Screen**: Displays a clean, transparent top bar with no `"Focus"` title text and the **Settings** icon button in the top-right corner.
  - **Challenges Screen**: Displays the full-width compact segmented pill tab bar (**Personal** | **Study Group**) at the top without a trailing Settings button.
  - **Progress Screen**: Displays the full-width compact segmented pill tab bar (**Stats** | **Exams** | **Island**) at the top without a `"Progress"` title bar or Settings button.
  - **Community Screen**: Displays the full-width compact segmented pill tab bar (**Leaderboard** | **Friends** with pending-request badge) at the top without a `"Community"` title bar or Settings button.
  - **Study Screen**: Starts directly with the Study content (Today card, search bar, and decks) below the status bar safe area without a `"Study"` title bar or Settings button.

## 2. User Experience & Visual Design

- **Visual Identity & Theme**:
  - *Pill Tab Styling*: Full-width rounded pill container (`CircleShape`, `MaterialTheme.colorScheme.surfaceContainer` with `3.dp` inner padding) holding equal-weight pill tabs that highlight the active tab with `MaterialTheme.colorScheme.primaryContainer` and bold `labelLarge` typography paired with a leading 16dp Material icon.
  - *Focus-Only Settings Action*: Only the **Focus** tab renders the top-right `Settings` `IconButton`, giving **Study**, **Challenges**, **Progress**, and **Community** full horizontal width and maximum vertical space.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Full-Width Pill Bar on `Challenges`, `Progress`, and `Community`**
  - *Chosen Approach*: Remove the trailing Settings `IconButton` from `ChallengesScreen` and render the segmented pill bar across the full width on `Challenges`, `Progress`, and `Community`, using `WindowInsets.statusBars` safe padding when the `TopAppBar` is absent.
  - *Why*: Gives 3-tab screens like **Progress** (`Stats` / `Exams` / `Island`) and 2-tab screens (`Challenges`, `Community`) generous horizontal room for labels and icons while saving ~64dp of vertical height.
- **Decision 2: Settings Button Exclusively on `Focus`**
  - *Chosen Approach*: Render the transparent `TopAppBar(title = {}, actions = { SettingsIconButton })` only when `current == TopLevel.FOCUS`. On all other tabs (`STUDY`, `CHALLENGES`, `PROGRESS`, `COMMUNITY`), no `TopAppBar` is shown and top status-bar inset padding is applied cleanly so content never overlaps the system status bar.
  - *Why*: Keeps the app chrome minimal and distraction-free while keeping Settings one tap away from the home Focus screen.

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           MainShell (AppNavGraph)                       │
├────────────────────────────────────┬────────────────────────────────────┤
│   Focus Tab (TopLevel.FOCUS)       │ Study, Challenges, Progress,       │
│   • Transparent TopAppBar          │ Community Tabs                     │
│   • Empty title = {}               │ • No TopAppBar & no Settings icon  │
│   • Top-right Settings IconButton  │ • Status bar safe-area padding     │
│                                    │ • Full-width Segmented Pill Tabs   │
│                                    │   on Challenges/Progress/Community │
└────────────────────────────────────┴────────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - `MainShell`:
    - Renders `TopAppBar(title = {}, actions = { SettingsIconButton })` only when `current == TopLevel.FOCUS && !isPhoneLockActive`.
    - Applies `statusBarsPadding()` to non-Focus tab content so removing the `TopAppBar` on `STUDY`, `CHALLENGES`, `PROGRESS`, and `COMMUNITY` never clips under the status bar.
  - `ChallengesScreen`:
    - Full-width compact segmented pill bar (`Personal` | `Study Group`) with `onOpenSettings = null`.
  - `ProgressTab`:
    - Replaces `PrimaryTabRow` with a full-width compact segmented pill bar (`Stats`, `Exams`, `Island` with icons).
  - `CommunityTab` & `CommunityScreen`:
    - Replaces `PrimaryTabRow` with a full-width compact segmented pill bar (`Leaderboard`, `Friends` with pending-request count badge).
