# Friend Daily Study Duels & Strict App-Switch Auto-Pause

This plan introduces real-time head-to-head daily study challenges between friends—initiated directly from the Leaderboard (Classement), tracked live alongside the Pomodoro timer, and archived in the Challenges (Défi) screen—along with automatic Pomodoro pause/resume whenever a user leaves Focus Island to use another app.

### User Review & Critical Decisions

> [!IMPORTANT]
> The following product behaviors were confirmed during clarification and govern the implementation:

- **Confirmed Decision 1 — Challenge Win Condition**: Friend challenges run until the end of the current day (midnight), and the friend who accumulates the most focus study minutes today wins.
- **Confirmed Decision 2 — Auto-Pause Trigger**: Leaving Focus Island to use any other app while the screen is active automatically pauses the Pomodoro timer until the user returns to Focus Island.
- **Confirmed Decision 3 — Surrender Outcome**: If one player surrenders during an active friend challenge, the opponent immediately wins and receives the winner celebration.

---

### 1. Overview & Core Concept

- **What It Does**:
  1. **Friend Study Challenge ("Who Studies More Today")**: Users can tap any friend in the Leaderboard (Classement) or Friends tab to challenge them for the day. Once the friend accepts, both players' Pomodoro timers start automatically, a live side-by-side progress tracker appears next to the Pomodoro dial, and the winner (by midnight or opponent surrender) receives a confetti and audio celebration. All past and active duels are accessible in the Challenges (Défi) screen.
  2. **Strict App-Switch Auto-Pause**: Whenever the Pomodoro timer is running and the user switches to any other app on their phone, the timer automatically pauses and notifies them. As soon as they return to Focus Island, the timer seamlessly resumes.
- **Target Audience / Persona**: Students and self-learners who thrive on friendly accountability with study partners and want strict protection against phone distractions during focus sessions.
- **Key Value**: Transforms passive leaderboards into live, synchronous daily competitions while enforcing honest, distraction-free study time.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Challenging from Classement (Leaderboard)**:
     - Tapping a friend's row in the Leaderboard (or Friends list) opens their profile sheet, now featuring a primary **"Challenge Today" (Défier aujourd'hui)** button.
     - Tapping it sends a real-time challenge invitation for today and displays a "Waiting for friend to accept" state.
  2. **Accepting & Auto-Starting the Pomodoro Timer**:
     - The invited friend sees an incoming Challenge Duel banner/dialog in Community, Focus, and Challenges screens with **Accept** and **Decline** actions.
     - Upon accepting, the challenge becomes active and **automatically starts the Pomodoro focus timer** for the duel.
  3. **Live Side-by-Side Progress Next to the Pomodoro Timer**:
     - While a friend challenge is active, the Focus screen (and full-screen Focus Mode) displays a compact **Live Friend Duel Card** directly alongside/below the Pomodoro timer ring.
     - It shows `You (X min)` vs `Friend (Y min)` with a two-tone comparative progress bar, live minute increments as the timer ticks, time remaining until midnight, and a quick action to view details or surrender.
  4. **Completion, Surrender & Winner Celebration**:
     - If a player taps **Surrender (Abandonner)** and confirms, the duel ends immediately: the surrendering player sees a defeat state, while the opponent immediately receives the **Winner Celebration** (confetti burst, style victory sound, and trophy banner).
     - At midnight, if neither player surrendered, the player with the higher focus minutes for the day triggers the Winner Celebration.
  5. **Challenge / Défi Screen History**:
     - The Challenges (Défi) screen displays active friend duels, pending invitations, and a **Friend Duels History** section listing completed challenges with date, opponent name, final minute comparison (`You vs Friend`), and outcome badge (`Won`, `Lost`, `Surrendered`).
  6. **Auto-Pause on Leaving App**:
     - If the user switches to another app while the timer is running (with the device screen on), the timer pauses immediately and updates the ongoing notification to indicate it paused because Focus Island left the foreground.
     - Returning to Focus Island automatically resumes the countdown and shows a non-intrusive status banner confirming resumption.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Harmonizes with Focus Island's four signature themes (Lagoon, Arcade, Blossom, Explorer), using Material 3 tonal surfaces and expressive progress indicators.
  - *Color Palette & Mood*: Uses `MaterialTheme.colorScheme.primary` for the user's progress bar, `MaterialTheme.colorScheme.tertiary` / `secondary` for the friend's progress bar, and `MaterialTheme.extendedColors.successContainer` for victory cards and badges.
  - *Typography & Hierarchy*: Tabular figures for live study minutes and countdown clocks; bold headline typography for victory banners and comparative score readouts.
  - *Component Styling & Layout*: Head-to-head progress bar with animated width transitions, initial-letter avatars on each side of the duel card, and adaptive placement in both portrait and landscape layouts.

- **Interactive Feedback & Motion**:
  - Full-screen particle confetti (`Celebration`) and themed audio (`playSuccessSound`) triggered automatically when a challenge is won (either at midnight or when the opponent surrenders).
  - Smooth animated transitions (`animateFloatAsState` / `AnimatedVisibility`) for live score bars and auto-pause/resume banners.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Real-Time Cloud Sync via Firestore for Friend Duels**
  - *Chosen Approach*: Store friend challenges in a top-level `friend_challenges` Firestore collection indexed with `participants: [challengerUid, opponentUid]` and real-time snapshot listeners.
  - *Why*: Allows both friends to see instant invitations, live minute updates, and immediate surrender/victory states across devices without polling.
  - *Alternatives Considered*: Local-only challenge tracking was rejected because both friends need live bidirectional updates when accepting, studying, or surrendering.

- **Decision 2: Distinguishing App Switching from Screen Lock for Auto-Pause**
  - *Chosen Approach*: Track app foreground state via `Application.ActivityLifecycleCallbacks` paired with `PowerManager.isInteractive`.
  - *Why*: If the user presses the power button to lock their screen while studying, the timer must keep running. If the screen is on (`isInteractive == true`) and all Focus Island activities move to the background (`onStop` without configuration change), the user has switched to another app (e.g., camera, social media, browser), so the timer auto-pauses and auto-resumes when Focus Island returns to the foreground.
  - *Alternatives Considered*: Pausing unconditionally on every `onStop` was passed over because locking the phone screen also triggers `onStop`, which would break normal screen-off Pomodoro sessions.

- **Decision 3: Live Study Minutes Calculation During Active Duels**
  - *Chosen Approach*: Combine completed focus session minutes from today with the currently elapsed focus minutes of an in-progress Pomodoro session, publishing periodic updates to the active duel document.
  - *Why*: Ensures the friend progress bar next to the Pomodoro timer feels responsive in real time rather than only jumping at the end of a 25-minute block.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                              UI Layer (Compose)                           │
│                                                                           │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌───────────────────┐  │
│  │   CommunityScreen   │  │     FocusScreen     │  │ ChallengesScreen  │  │
│  │  (Classement Tap -> │  │ (Pomodoro + Friend  │  │ (Solo + Friend    │  │
│  │   Challenge Friend) │  │  Live Progress Bar  │  │  Duel Active &    │  │
│  │                     │  │  + AutoPause Banner)│  │  History List)    │  │
│  └──────────┬──────────┘  └──────────┬──────────┘  └─────────┬─────────┘  │
└─────────────┼────────────────────────┼───────────────────────┼────────────┘
              │                        │                       │
              ▼                        ▼                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                        ViewModel & Service Layer                          │
│                                                                           │
│  ┌──────────────────────────┐          ┌───────────────────────────────┐  │
│  │   ChallengeViewModel &   │◄────────►│     PomodoroTimerService      │  │
│  │    CommunityViewModel    │          │  - Auto-Start on Duel Accept  │  │
│  │  - Send / Accept /       │          │  - Auto-Pause on App Switch   │  │
│  │    Decline / Surrender   │          │  - Auto-Resume on App Return  │  │
│  └────────────┬─────────────┘          └───────────────┬───────────────┘  │
└───────────────┼────────────────────────────────────────┼──────────────────┘
                │                                        │
                ▼                                        ▼
┌────────────────────────────────────────┐ ┌────────────────────────────────┐
│   FriendChallengeRepository (Cloud)    │ │ AppForegroundTracker & Room DB │
│  - Firestore `friend_challenges`       │ │  - ActivityLifecycleCallbacks  │
│  - Real-time Snapshot Listeners        │ │  - PowerManager.isInteractive  │
│  - Live Today's Minutes Sync           │ │  - Today's Focus Sessions      │
└────────────────────────────────────────┘ └────────────────────────────────┘
```

- **Data Model & State**:
  - **`FriendChallenge` Document (`friend_challenges/{challengeId}`)**:
    - `id: String` — Document identifier (`{dayKey}_{challengerUid}_{opponentUid}`).
    - `participants: List<String>` — Two-element list `[challengerUid, opponentUid]` for single-query observation of incoming, active, and historical challenges.
    - `challengerUid: String`, `challengerName: String`, `challengerMinutes: Int`
    - `opponentUid: String`, `opponentName: String`, `opponentMinutes: Int`
    - `dayKey: String` — ISO local date (`YYYY-MM-DD`).
    - `createdAt: Long`, `acceptedAt: Long?`, `endAt: Long` (midnight timestamp), `finishedAt: Long?`
    - `status: String` — `"pending"`, `"active"`, `"completed"`, `"surrendered"`, `"declined"`.
    - `surrenderedByUid: String?`, `winnerUid: String?`
  - **`TimerState` Additions**:
    - `isAutoPausedByAppSwitch: Boolean` — Tracks whether the current pause was triggered automatically by leaving Focus Island so returning to the app automatically resumes it.

- **Interactive Component & State Mapping**:
  - **Leaderboard (Classement) Friend Tap**: In `LeaderboardRow`, tapping any row where `relation == Relation.FRIEND` opens `FriendSheet` with a primary `"Challenge Friend"` button (disabled if a duel with that friend is already active today).
  - **Challenge Invitation Banner**: Observed globally in `ChallengeViewModel` and `CommunityViewModel`. Accepting updates Firestore `status = "active"`, sets `acceptedAt`, and dispatches `PomodoroTimerService.ACTION_START` on both clients when the state transitions to `"active"`.
  - **Live Progress Sync & Side-by-Side Widget**: While an active `FriendChallenge` exists, `FocusScreen` and `FocusModeScreen` render `FriendDuelProgressCard` next to the timer, comparing `myMinutesToday` vs `friendMinutesToday`. Every minute of active focus (and on session completion), the user's today minutes are pushed to the active challenge document.
  - **Surrender & Midnight Resolution**: Tapping Surrender prompts a confirmation dialog, then updates `status = "surrendered"`, `surrenderedByUid = myUid`, and `winnerUid = friendUid`. When either player observes a challenge transition to `"surrendered"` or `"completed"` where `winnerUid == myUid`, `Celebration` confetti and victory sound play immediately, and the completed challenge appears in the Challenges/Défi history list.
  - **App-Switch Auto-Pause**: `FocusFlowApplication` registers `ActivityLifecycleCallbacks`. When the started activity count drops to `0` while `powerManager.isInteractive == true` and `!activity.isChangingConfigurations`, it dispatches `ACTION_AUTO_PAUSE` to `PomodoroTimerService`. When the started activity count rises from `0` to `1`, if `state.isAutoPausedByAppSwitch` is true, it dispatches `ACTION_AUTO_RESUME`.
