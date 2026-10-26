# Multi-Friend Study Group Challenges & UI Fixes

This update resolves layout and lifecycle bugs around launching and re-launching friend challenges, restores full vertical space to the Pomodoro timer circle on the Focus screen, and upgrades the daily 1v1 duel into a real-time **Study Group Challenge** where one or multiple friends can study together, see live focus status, exchange custom messages and quick emojis, and send sound-and-vibration focus nudges when a friend is paused or idle.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions were confirmed during clarification and govern this implementation:

- **Shared Live Study Group Room**: When starting a challenge with one or multiple friends, a live Study Group room opens immediately and invited friends can join at any time during the day.
- **Custom Text + Quick Emoji Chat**: Group members can send both free-form custom text messages and one-tap motivational emojis inside the active Study Group card on the Challenges screen.
- **Pause / Idle Focus Nudge with Sound, Vibration & Cooldown**: Whenever a group member's Pomodoro timer is paused or idle (not actively focusing), other members can tap a **Nudge** button next to that friend (with a per-friend cooldown to prevent spam), triggering a high-priority Android notification with sound and vibration on the recipient's phone to remind them to start focusing.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Fixes Cut-Off Friend Challenge Sheet**: Ensures the friend detail bottom sheet on the Community screen expands fully, scrolls when needed, and pads above the system 3-button navigation bar so the challenge button is always visible and tappable.
  2. **Fixes Re-Launching After Abandoning a Challenge**: Generates unique challenge session IDs and resets active duel state on surrender so users can immediately launch a new challenge on the same day after abandoning a previous one.
  3. **Keeps Challenge Progress Exclusively on the Challenges Page**: Removes the live/incoming challenge cards from the Focus screen so the Pomodoro timer dial never shrinks or gets crowded.
  4. **Upgrades Challenges into a Multi-Friend Study Group**: Lets users invite one or multiple friends into a shared live study group showing each member's focus minutes and live timer state (`Focusing`, `Paused`, or `Idle`), a real-time chat & emoji feed, and a **Nudge** button that rings and vibrates a paused/idle friend's device.
- **Target Audience / Persona**: Students and study partners using Focus Island across phones of varying screen heights and navigation bar modes who want real-time accountability with their friend group without sacrificing the clean Pomodoro timer view.
- **Key Value**: Uncluttered focus sessions paired with an interactive, multi-person study room that keeps friends accountable through live status, encouragement, and gentle sound/vibration nudges.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Launching from Community or Challenges Screen**:
     - Tapping a friend in the Community tab opens the fully expanded, navigation-bar-safe profile sheet where the user can tap **Challenge** (or invite them into the current Study Group).
     - On the **Challenges (`Défis`)** tab, users can tap **Create / Invite to Study Group** to select **one or multiple friends** via checkboxes and launch or add them to today's live Study Group room.
  2. **Re-Launching After Abandoning**:
     - If a user taps **Abandon / Surrender** on an active challenge, that session is marked ended and cleared from the active slot. The user can immediately start a new Study Group or challenge the same or different friends without being blocked.
  3. **Live Study Group Progress & Pause Nudge**:
     - Inside the **Challenges** tab, the Study Group card displays a leaderboard of all participants sorted by today's focus minutes, with a live status pill (`Focusing`, `Paused`, or `Idle`) and progress bar toward the daily goal.
     - Next to any friend whose status is `Paused` or `Idle`, a **Nudge (Remind to Focus)** icon/button appears. Tapping it sends a real-time nudge event and enters a 60-second cooldown.
     - When the friend's device receives the nudge event, it triggers an immediate vibration pattern, notification sound, and high-priority Android notification (*"<Friend> nudged you to start focusing!"*) that opens the Focus screen when tapped.
  4. **Study Group Chat & Quick Emojis**:
     - Below the member progress list in the Study Group card, a compact scrollable chat feed shows recent messages and emoji reactions with sender name and relative time.
     - A quick-emoji bar (`🔥`, `💪`, `📚`, `☕`, `👏`, `⚡`) allows one-tap reactions, alongside a text field and send button for custom messages.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Cohesive Material 3 surfaces (`surfaceContainerLow`, `secondaryContainer`, `primaryContainer`) that adapt seamlessly across Light and Dark modes and all four Focus Island visual styles.
  - *Component Styling & Layout*:
    - Safe-area-aware `ModalBottomSheet` using `skipPartiallyExpanded = true`, `verticalScroll`, and `WindowInsets.navigationBars` padding.
    - Clean Focus screen dedicated exclusively to the Pomodoro dial, transport controls, and the Sound/Today diaporama.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unique Session IDs & Timestamp-Aware State Resolution on Surrender**
  - *Chosen Approach*: Append a creation timestamp token to new challenge IDs (`<dayKey>_<creatorUid>_<epochSec>`) and prioritize newer active/pending challenges over older surrendered ones from the same day. When surrendering, clear or mark the user's active challenge pointer as ended so `hasActiveDuel` becomes `false` immediately.
  - *Why*: Previously, deterministic IDs (`<dayKey>_<myUid>_<opponentUid>`) and a fixed `statusRank` (`SURRENDERED = 5 > PENDING = 1`) caused any newly created same-day challenge to collide with and be overwritten by the abandoned challenge.
- **Decision 2: Dual-Sync Real-Time Study Group State (Firestore Challenges + User Profile Mirror)**
  - *Chosen Approach*: Store the Study Group room in `/friend_challenges/{challengeId}` (with `participants` list, `memberStates` map, `messages` list, and `nudges` map) while also broadcasting each user's live focus state (`isFocusing`, `isPaused`, `todayMinutes`, `activeGroupId`, `lastNudge`) on their `/users/{uid}` document and listening to all group members' profiles in real time.
  - *Why*: Guarantees real-time sync of multi-friend progress, chat messages, and focus nudges even across strict Firestore security rules, while keeping bandwidth minimal.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         UI Layer (Jetpack Compose)                        │
├───────────────────────┬───────────────────────────┬───────────────────────┤
│     FocusScreen       │     ChallengesScreen      │    CommunityScreen    │
│  (Pomodoro Circle     │  ┌─────────────────────┐  │  ┌─────────────────┐  │
│   Only — Duel Cards   │  │ StudyGroupLiveCard  │  │  │   FriendSheet   │  │
│   Removed for Full    │  │ • Multi-Friend List │  │  │ • skipPartially │  │
│   Dial Sizing)        │  │ • Live Status Pills │  │  │   Expanded=true │  │
│                       │  │ • Nudge 🔔 Button   │  │  │ • verticalScroll│  │
│                       │  │ • Quick Emoji Bar   │  │  │ • navBar Insets │  │
│                       │  │ • Custom Chat Input │  │  └─────────────────┘  │
│                       │  └─────────────────────┘  │                       │
└───────────┬───────────┴─────────────┬─────────────┴───────────┬───────────┘
            │                         │                         │
            ▼                         ▼                         ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                  PomodoroTimerService & ChallengeViewModel                │
│  • Publishes live timer state (RUNNING / PAUSED / IDLE) + today's minutes │
│  • Observes incoming Nudge events → triggers Notification + Sound + Vibe  │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         FriendChallengeRepository                         │
│  • Multi-friend Study Group creation & mid-session friend invitations     │
│  • Unique challenge IDs so surrendered challenges never block new ones    │
│  • Real-time Firestore listeners for participants, chat messages & nudges │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

- **`StudyGroupMember`**:
  - `uid`, `name`, `minutes`, `status` (`INVITED`, `ACTIVE`, `SURRENDERED`), `timerStatus` (`FOCUSING`, `PAUSED`, `IDLE`), `updatedAt`.
- **`StudyGroupMessage`**:
  - `id`, `senderUid`, `senderName`, `text`, `isEmojiOnly`, `createdAt`.
- **`StudyGroupNudge`**:
  - `id`, `fromUid`, `fromName`, `toUid`, `createdAt`.
- **`FriendChallenge` (Extended for Study Groups)**:
  - Retains full backward compatibility with 1v1 duels while adding `members: List<StudyGroupMember>`, `messages: List<StudyGroupMessage>`, and `latestNudges: Map<String, StudyGroupNudge>` so 1 or multiple friends work seamlessly in the same model.

### Interactive Component & State Mapping

1. **Friend Profile Bottom Sheet (`CommunityScreen`)**:
   - Uses `rememberModalBottomSheetState(skipPartiallyExpanded = true)` and wraps content in a vertically scrollable column with `navigationBarsPadding()` and bottom spacing so the challenge button is never clipped behind the 3-button system bar.
2. **Focus Screen (`FocusScreen`)**:
   - Removes `friendDuelPanel()` from `FocusContent` so the Pomodoro `TimerDial` always receives the full available vertical weight.
3. **Abandon & Re-Launch Flow (`FriendChallengeRepository` & `ChallengeViewModel`)**:
   - Surrendering marks the current user's participation as surrendered and clears the active challenge slot; launching a new challenge creates a fresh timestamped challenge ID and resets status to `PENDING`/`ACTIVE` cleanly.
4. **Multi-Friend Selector, Live Status, Chat & Nudge (`ChallengesScreen` & `FriendDuelComponents`)**:
   - Multi-select friend picker sheet lets users pick 1 or more friends to start a new Study Group or add friends to an active group.
   - `PomodoroTimerService` syncs timer running/paused/idle status to `FriendChallengeRepository` whenever the user starts, pauses, resumes, or stops the Pomodoro timer.
   - Incoming nudge observer in `FriendChallengeRepository` compares incoming nudge timestamps for `myUid` against the last handled nudge timestamp and fires a high-priority system notification with default notification ringtone and vibration pattern.
