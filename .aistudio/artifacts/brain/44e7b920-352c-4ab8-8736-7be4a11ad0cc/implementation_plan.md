# Automatic Screen Unpinning & Ephemeral Lock Phone Sessions

When a Lock Phone countdown finishes, the app will automatically exit Android Screen Pinning (`stopLockTask()`) across every timer expiration path so you can immediately close the app or return to the Android home screen without seeing the system *"Pour que l'appli ne soit plus épinglée, balayez-la vers le haut et maintenez le doigt appuyé"* prompt, and Lock Phone sessions will not be saved in the personal challenge history record.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Confirmed Decision 1 (Auto-Unpin on Completion)**: Keep Android Screen Pinning active during the Lock Phone countdown, and automatically unpin the app (`stopLockTask()`) the moment the timer reaches `00:00` so the app is never left pinned afterward.
> - **Confirmed Decision 2 (Do Not Save Lock Phone Records)**: Because a Lock Phone session strictly forces completion, it does not need to be logged in the personal challenge history list. Only "Set a Goal" (`GOAL`) challenges are recorded in history.

## 1. Overview & Core Concept

- **Root Cause of Unpin Prompt**: Previously, when the countdown reached `00:00`, the background timer service (`PomodoroTimerService`), alarm receiver (`ChallengeAlarmReceiver`), or parent `ChallengesScreen` expired the active challenge first (`state.active = null`). This immediately removed `StrictPhoneLockOverlay` from the UI before its internal completion effect could call `stopLockTask()`, and `PhoneLockOverlayManager.dismissShield()` did not hold a reference to the pinned `Activity` to unpin it.
- **Solution**:
  1. Track the pinned `Activity` directly inside `PhoneLockOverlayManager`, unwrap `ContextWrapper` chains reliably to locate the host `Activity`, and invoke `stopLockTask()` automatically in `dismissShield()`, on `StrictPhoneLockOverlay` disposal, in `ChallengesScreen` completion handlers, and in `MainActivity` / `FocusOverlayActivity` lifecycle hooks whenever no Lock Phone session is active.
  2. Update `ChallengeStore` so finishing or expiring a `LOCK_PHONE` session simply clears `active = null` without adding the session to `history` (and filters out any `LOCK_PHONE` entries from local/remote history).

## 2. User Experience & Visual Design

- **Seamless Timer Completion**:
  1. While the Lock Phone countdown is running, the screen remains strictly locked and pinned, displaying only the circular countdown counter and the session's motivational quote.
  2. The instant the countdown reaches `00:00`, the system overlay shield is dismissed, Android Screen Pinning is stopped (`stopLockTask()`), system navigation/status bars are restored, and the active lock session is cleared without adding a history card.
  3. Swiping up to go Home, opening Recent Apps, or closing the app works immediately without any Android "swipe up and hold to unpin" toast.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Centralized Activity Tracking in `PhoneLockOverlayManager`**
  - *Chosen Approach*: Store a `WeakReference<Activity>` to the pinned activity whenever `tryStartScreenPinning()` is called or when `MainActivity` / `FocusOverlayActivity` is active, and automatically call `stopLockTask()` inside `dismissShield()`.
  - *Why*: Timer expiration can be triggered first by `PomodoroTimerService` (1-second ticker), `ChallengeAlarmReceiver` (exact alarm), or the Compose UI loop. Centralizing unpinning inside `dismissShield()` guarantees that whichever component expires the timer first will also unpin the `Activity` on the main thread.
- **Decision 2: Compose Disposal & Activity Lifecycle Safety Nets**
  - *Chosen Approach*: Call `tryStopScreenPinning()` inside `StrictPhoneLockOverlay`'s `DisposableEffect.onDispose`, inside `ChallengesScreen` when `state.active` clears, and inside `MainActivity` / `FocusOverlayActivity` lifecycle callbacks (`onResume`, `onWindowFocusChanged`) whenever `isPhoneLockActive()` is false.
  - *Why*: Eliminates any race condition where the Compose lock screen unmounts before the countdown callback fires.
- **Decision 3: Ephemeral Lock Phone Sessions (No History Record)**
  - *Chosen Approach*: In `ChallengeStore.expireIfDue()`, `finish()`, and `start()`, when the challenge is `PersonalChallengeType.LOCK_PHONE`, set `active = null` while leaving `history` untouched, and exclude `LOCK_PHONE` challenges when loading or syncing history.
  - *Why*: Keeps the Personal Challenges history dedicated to actual "Set a Goal" challenges where the user races the clock to beat a goal.

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     Lock Phone Timer Reaches 00:00                       │
│  (Triggered by ChallengesScreen, PomodoroTimerService, or AlarmReceiver) │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
                   ┌─────────────────┴─────────────────┐
                   ▼                                   ▼
┌─────────────────────────────────────┐ ┌──────────────────────────────────┐
│           ChallengeStore            │ │     PhoneLockOverlayManager      │
│  • expireIfDue() / finish() clears  │ │  • Removes WindowManager overlay │
│    active = null                    │ │  • Calls stopLockTask() on       │
│  • Does NOT append LOCK_PHONE       │ │    pinnedActivityRef on Main     │
│    sessions to history              │ │    Looper                        │
└─────────────────────────────────────┘ └──────────────┬───────────────────┘
                                                       │
                                     ┌─────────────────┴─────────────────┐
                                     ▼                                   ▼
                   ┌───────────────────────────────────┐ ┌──────────────────────────────────┐
                   │      StrictPhoneLockOverlay       │ │ MainActivity & FocusOverlay      │
                   │  • Context.findActivity() unwraps │ │  • Unpins in onResume /          │
                   │    ContextWrapper chains          │ │    onWindowFocusChanged if       │
                   │  • DisposableEffect.onDispose     │ │    !isPhoneLockActive()          │
                   │    calls tryStopScreenPinning()   │ │  • Restores system bars          │
                   └───────────────────────────────────┘ └──────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - `Context.findActivity()`: Recursively unwraps `ContextWrapper` instances so `LocalContext.current` and `LocalView.current.context` always resolve to the host `Activity` even when locale wrappers are active.
  - `PhoneLockOverlayManager.tryStartScreenPinning(activity)`: Records `pinnedActivityRef = WeakReference(activity)` and invokes `activity.startLockTask()` if not already in LockTask mode.
  - `PhoneLockOverlayManager.tryStopScreenPinning(activity?)`: Resolves either the passed `Activity` or `pinnedActivityRef.get()`, checks `ActivityManager.lockTaskModeState != LOCK_TASK_MODE_NONE`, and invokes `stopLockTask()` on the main thread.
  - `PhoneLockOverlayManager.dismissShield(context)`: Removes the `TYPE_APPLICATION_OVERLAY` view and invokes `tryStopScreenPinning()` so background service/alarm expiration always unpins the app.
  - `ChallengeStore`: Keeps `active` persisted while a `LOCK_PHONE` session is running (so reboots or app restarts cannot bypass the lock), and clears `active = null` upon completion without saving a history item.
