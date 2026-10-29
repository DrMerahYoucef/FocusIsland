# Implementation Plan: Add More Target Focus Time to Active Challenges

## Overview
After a Study Group / Friend Challenge has been initialized, the creator (`challengerUid`) will be able to add more target focus time to the active challenge at any time before it ends.

## User Experience & Design
1. **Creator Entry Points in the Active Challenge Room (`FriendDuelLiveCard`)**:
   - **Quick-Access Header Action**: For the challenge creator (`isCreator == true`), the target/mode badge in the top bar (and a dedicated **Add Time (+)** icon button / overflow menu item `"Add More Time"`) will open the **Add Challenge Time** modal bottom sheet.
   - Non-creators continue to see the current target goal badge in read-only mode.
2. **Add Challenge Time Sheet (`AddChallengeTimeSheet`)**:
   - Displays the current target focus goal (or indicates that adding time to an Open Challenge sets a target focus goal).
   - **Quick Increment Chips**: One-tap selectable chips (`+30m`, `+1h`, `+2h`, `+3h`, `+4h`) that populate the hours/minutes fields.
   - **Custom Time Input**: Hours (`0–24`) and Minutes (`0–59`) numeric fields allowing any custom increment (capped so total target duration stays within `24h` / `1440m`).
   - Live preview showing the **New Target Goal** (e.g., `Current: 2h -> New Target: 3h 30m`).
   - Primary confirmation button (`study_group_confirm_add_time_button`) to apply the extra time.
3. **Real-Time Sync & Group Chat Notice**:
   - Calling `addTargetMinutesByCreator(challenge, additionalMinutes)` in `FriendChallengeRepository` verifies the caller is the challenge creator (`isChallenger`), increments `targetMinutes` (ensuring it is also strictly above any participant's already-logged minutes if needed, up to `MAX_MINUTES`), and posts an automatic update message in the group chat (e.g., `"⏱️ Added +1h to the challenge target (New goal: 3h)"`) so all participants see the updated target in real time and receive a message alert.

## Key Files to Modify
- **`/app/src/main/res/values/strings.xml` & `/app/src/main/res/values-fr/strings.xml`**:
  - Add English and French string resources for the "Add Time" button/menu action, bottom sheet title/subtitle, quick chips, new target preview, and chat update announcement.
- **`/app/src/main/java/com/example/data/challenge/FriendChallengeRepository.kt`**:
  - Add `suspend fun addTargetMinutesByCreator(challenge: FriendChallenge, additionalMinutes: Int, announcementText: String = ""): Result<FriendChallenge>` to validate creator ownership, increment `targetMinutes`, append the optional group chat update message, update local cache, and sync to `/users/{myUid}` and `/friend_challenges/{id}`.
- **`/app/src/main/java/com/example/ui/screen/challenge/ChallengeViewModel.kt`**:
  - Add `fun addStudyGroupTime(challenge: FriendChallenge, additionalMinutes: Int, announcementText: String)` delegating to `friendRepo.addTargetMinutesByCreator(...)`.
- **`/app/src/main/java/com/example/ui/screen/challenge/FriendDuelComponents.kt` & `/app/src/main/java/com/example/ui/screen/challenge/ChallengesScreen.kt`**:
  - Wire `onAddTime: (FriendChallenge, Int) -> Unit` into `ChallengesContent`, `StudyGroupTabContent`, and `FriendDuelLiveCard`.
  - Add creator-only "Add More Time" button/menu item and interactive target badge in `FriendDuelLiveCard`, plus the `AddChallengeTimeSheet` composable with quick increment chips (`+30m`, `+1h`, `+2h`, `+3h`, `+4h`) and custom hours/minutes inputs.
- **`/app/src/test/java/com/example/StudyGroupChallengeTest.kt`**:
  - Add unit/Robolectric tests verifying creator-only permission enforcement, target focus time extension, and UI interactions.

## Verification Plan
- Run `compile_applet` to verify there are no compilation or resource errors.
- Run `gradle :app:testDebugUnitTest --tests com.example.StudyGroupChallengeTest` to verify the new creator time-extension logic and UI flow.
