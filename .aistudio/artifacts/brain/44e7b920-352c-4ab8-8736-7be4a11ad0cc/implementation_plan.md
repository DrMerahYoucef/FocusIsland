# Challenge Zero-Minute Baseline & Saved Challenge Focused Time Plan

## Goal
Ensure that when any challenge is created, **every participant starts at `0m`**, so hours and minutes focused earlier in the day before the challenge started do not count toward the challenge. When a challenge is completed and saved, freeze each participant's challenge-specific focused time and clearly display each participant's focused time in the saved challenge details.

---

## Proposed Changes

### 1. Track Per-Participant Baseline Minutes & Start Challenges at `0m`
**File:** `/app/src/main/java/com/example/data/challenge/FriendChallengeRepository.kt`

- **Add `baselineMinutes` to `StudyGroupMember`**:
  - Extend `StudyGroupMember` with `val baselineMinutes: Int = 0` and helper `fun challengeMinutesFromToday(todayTotalMinutes: Int): Int = (todayTotalMinutes - baselineMinutes).coerceAtLeast(0)`.
  - Serialize and deserialize `"baselineMinutes"` in `membersToJsonArray(...)` and `parseMembersJson(...)`, and preserve `baselineMinutes` when merging member lists in `mergeChallenges(...)`.
- **Initialize All Participants at `0m` on Challenge Creation (`startOrInviteToStudyGroup`)**:
  - When creating a new challenge, record the creator's current daily total (`myMins`) in `baselineMinutes` and set `minutes = 0` (and `challengerMinutes = 0`).
  - For every invited friend, record their current daily total (`maxOf(f.todayMinutes, liveFriend?.todayMinutes ?: 0)`) in `baselineMinutes` and set `minutes = 0` (and `opponentMinutes = 0`).
  - When inviting additional friends to an already-active challenge, preserve existing members' `baselineMinutes` from challenge creation and capture the baseline for any newly invited friend.
- **Count Only Post-Creation Focus Time (`acceptChallenge`, `observeTodayFocusAndTimerState`, `enrichChallengeWithLiveProfiles`, `rebuildState`)**:
  - When an invited friend accepts a challenge (`acceptChallenge`), calculate their current challenge minutes relative to the `baselineMinutes` recorded when the creator initialized the challenge (`member.challengeMinutesFromToday(myMins)`).
  - In `observeTodayFocusAndTimerState`, compute the current user's challenge minutes by subtracting their `baselineMinutes` in the active challenge from `totalTodayMinutes`, and push only those challenge-specific minutes (`myChallengeMinutes`) to Firestore via `pushMyStateToActiveChallenge(...)`.
  - In `enrichChallengeWithLiveProfiles` and `rebuildState`, compute live participant minutes for `ACTIVE` and `PENDING` challenges using `member.challengeMinutesFromToday(liveTodayMinutes)`.
- **Freeze Saved Challenge Minutes on Completion**:
  - In `enrichChallengeWithLiveProfiles` and `rebuildState`, stop overwriting `minutes`, `challengerMinutes`, and `opponentMinutes` with live `todayMinutes` once a challenge is in a terminal state (`COMPLETED`, `SURRENDERED`, `DECLINED`, or `CANCELLED`), ensuring saved challenges permanently retain the exact minutes focused during that challenge.

---

### 2. Display Each Participant's Challenge Focused Time in Saved Challenge Details
**Files:**
- `/app/src/main/java/com/example/ui/screen/challenge/FriendDuelComponents.kt`
- `/app/src/main/res/values/strings.xml`

- **Challenge Podium & Rankings Sheet (`ChallengePodiumSheet` & `PodiumStandingRow`)**:
  - Display each participant's formatted challenge focused duration (`formatStudyMinutesText(participant.minutes)`, e.g. `1h 20m` or `45m`) on the 1st/2nd/3rd place podium cards (`PodiumColumnItem`) and in a dedicated focused-time badge on the right side of every participant row in `PodiumStandingRow`.
- **Saved Challenge History Card (`FriendDuelHistoryRow`)**:
  - Format the saved challenge score summary using `formatStudyMinutesText(...)` for each participant so the history list and the podium detail sheet consistently show the focused time earned during that challenge.

---

## Verification Plan
- Run `compile_applet` to verify the project compiles cleanly.
- Run unit tests via `gradle :app:testDebugUnitTest` to verify challenge creation, live progress calculation, and saved challenge state handling.
