# Challenge Invitations, Initialization Modes & Chat Space Redesign Plan

## 1. Floating Challenge Invitation Capsule & Notification Flow
- **Global In-App & System Floating Invite Capsule**:
  - Create `ChallengeInviteFloatingCapsule` (in `FriendDuelComponents.kt`, hosted at the root of `AppNavGraph.kt`) and a matching system overlay in `FloatingFocusBubbleManager.kt` when the app is in the background.
  - Styled with the active `AppStyle` tonal surface (`surfaceContainerHigh`) and `primary` accent border, displaying the challenger's name, challenge duration/mode summary, and remaining invite timer.
  - Includes three direct actions:
    - **Accept**: Immediately accepts the invitation and opens the active Study Group room.
    - **Deny**: Declines the invitation and dismisses the capsule.
    - **Hide**: Dismisses the floating overlay capsule while keeping the invitation active until its 30-minute timer expires.
- **Post-Hide Experience (Focus Screen Overlay, Challenges Tab & Nav Badge)**:
  - **Focus Screen Dismissible Invite Overlay**: When an invitation is hidden from the global floating capsule (or pending while on the Focus tab), display a compact, sleek invite banner at the top of `FocusScreen.kt` with **Accept**, **Deny**, and its own **Dismiss (`X`)** button so the user can also clear it from the Focus screen without declining.
  - **Challenges Tab Persistence**: The full `FriendDuelIncomingCard` remains visible in the **Challenges** tab for accepting or declining at any time before expiration.
  - **Bottom Navigation & Navigation Rail Badge**: Add a Material 3 `BadgedBox` with a live count `Badge` on the **Challenges** icon in `AppNavGraph.kt` (`NavigationBar` and `NavigationRail`) whenever `friendChallengeState.incomingPending` is non-empty.

---

## 2. Challenge Initialization: Duration Modes & Starting Progress
- **Three Duration / End Modes in `ChallengeInviteModeSelector`**:
  1. **Whole Day (`ALL_DAY`)**: Runs until midnight (`00:00` at the end of the day); the participant with the most focus time accumulated by the end of the day wins.
  2. **Preset / Custom Hours (`TARGET_HOURS`)**: Choose from quick duration chips (`30m`, `1h`, `2h`, `3h`, `4h`) or enter custom hours and minutes (`targetMinutes > 0`).
  3. **Until a Defined Day Hour (`UNTIL_CLOCK_TIME`)**: Pick a specific clock hour today (via quick evening/afternoon hour chips or an interactive Material 3 `TimePicker` dialog, e.g., `18:00`). Sets `endAt` to that exact clock time with `targetMinutes = 0`; the winner is the participant with the **most focus time accumulated when that hour arrives**.
- **Starting Progress Option (`startFromZero` vs. `Keep Day Progress`)**:
  - Add a two-option segmented selector in `ChallengeInviteModeSelector` (available in both `StudyGroupFriendsSheet` in `ChallengesScreen` and `FriendSheet` in `CommunityScreen`):
    - **Start from Zero (`0m`)**: Sets each participant's `baselineMinutes` to their focus minutes already completed before `challenge.createdAt`, so only post-creation focus time counts.
    - **Keep Day Progress**: Sets `baselineMinutes = 0` for all participants (`startFromZero = false` stored on `FriendChallenge` in Firestore and local state), counting focus time already accumulated earlier that day.
  - Display a clear mode pill on incoming invite cards and active room headers indicating both the end condition (All Day, Target Goal, or Until `HH:mm`) and progress mode (From `0m` vs. Full Day Progress).

---

## 3. Challenges Screen & Study Group Chat UX Redesign
- **Unified Single-Row Top Bar & Tab Switcher**:
  - Remove the redundant `64.dp` outer `TopAppBar` when viewing the `Challenges` tab in `AppNavGraph.kt`.
  - Replace the stacked title bar + `PrimaryTabRow` with a single, sleek `52.dp` header bar inside `ChallengesScreen.kt` housing a pill-style segmented tab switcher (`Personal` | `Study Group` with live indicator dots/badges) on the left and the `Settings` icon button on the right.
- **Ultra-Compact Collapsible Study Group Room Header**:
  - Redesign the top section of `StudyGroupActiveCard` (`FriendDuelComponents.kt`) into a compact single-row status header (`44.dp`) showing the room title, live countdown/deadline badge, current #1 leader pill, and inline icon actions (`+Time`, `+Friends`, `More`).
  - Make the live leaderboard and pending-invite chips collapse into a single interactive summary pill by default once chat is active (and expandable/collapsible with a single tap), giving the **Study Group Chat** up to ~80% of the screen height.
- **Localization & Verification**:
  - Add all new English (`values/strings.xml`) and French (`values-fr/strings.xml`) string resources, preserve all Compose `testTag` identifiers, and verify with unit tests (`StudyGroupChallengeTest.kt`) and `compile_applet`.
