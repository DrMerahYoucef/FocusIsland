# Lock-Screen Pomodoro Continuity & Display Plan

## 1. Goal
Ensure the Pomodoro timer continues running uninterrupted when the user locks their phone, only auto-pauses when genuinely switching to another app while unlocked, and displays both the full-screen Pomodoro timer over the lock screen and a live lock-screen notification with playback controls.

## 2. Technical Architecture & Key Changes

### A. Prevent Auto-Pause When Locking the Phone (`FocusFlowApplication.kt` & `PomodoroTimerService.kt`)
- **Debounce & Keyguard Check on `onActivityStopped`**:
  - When the user presses the Power button to lock the device, `onActivityStopped` can fire milliseconds before `PowerManager.isInteractive` changes to `false` or while `KeyguardManager` is engaging.
  - Replace the immediate check in `FocusFlowApplication.registerAppSwitchAutoPause()` with a short debounced check (400 ms) on `applicationScope` that verifies:
    1. `startedActivitiesCount == 0`
    2. `PowerManager.isInteractive == true`
    3. `KeyguardManager.isKeyguardLocked == false` and `KeyguardManager.isDeviceLocked == false`
  - Only send `ACTION_AUTO_PAUSE` if all three conditions hold after the debounce window (meaning the user genuinely switched to another app on an unlocked screen).
- **Screen-Off Safety Guard (`PomodoroTimerService.kt`)**:
  - Register a receiver in `PomodoroTimerService` for `Intent.ACTION_SCREEN_OFF`. If the timer was just marked `isAutoPausedByAppSwitch` within the last 2 seconds when the screen turns off, immediately auto-resume it (`autoResumeFromAppSwitch()`) so a race between locking the phone and activity stop never pauses the countdown.

### B. Full-Screen Pomodoro Timer on the Lock Screen (`AndroidManifest.xml`, `MainActivity.kt`, `AppNavGraph.kt`)
- **Lock-Screen Window Flags**:
  - Add `android:showWhenLocked="true"` to `MainActivity` in `AndroidManifest.xml` and call `setShowWhenLocked(true)` (with `FLAG_SHOW_WHEN_LOCKED` fallback) in `MainActivity.onCreate()`.
  - This allows `MainActivity` and `FocusModeScreen` to render directly over the lock screen when the phone is locked and the screen is turned on.
- **Lock-Screen Focus Mode Presentation**:
  - When the device is locked (`KeyguardManager.isKeyguardLocked == true`) and a Pomodoro session is active (`state.isRunning` or `!state.isUntouched`), automatically surface the Focus screen / `FocusModeScreen` so the live phase ring, countdown clock, and Play/Pause/Skip controls are immediately interactive on the lock screen.

### C. Live Lock-Screen Notification & Controls (`PomodoroTimerService.kt`)
- **Public Lock-Screen Visibility**:
  - Set `NotificationCompat.VISIBILITY_PUBLIC` on the timer `NotificationCompat.Builder` and `Notification.VISIBILITY_PUBLIC` on the `NotificationChannel` so the countdown and controls are always visible on the lock screen.
- **Per-Second Live Countdown & Progress Bar**:
  - Update the foreground notification in `startTicker()` so the title/content text includes the live `MM:SS` remaining time alongside the chronometer and `.setProgress(totalSeconds, elapsedSeconds, false)`, plus **Pause / Resume**, **Skip**, and **End** action buttons that work directly from the lock screen.

## 3. Verification Plan
- Add a unit/Robolectric test verifying that locking the screen (`isKeyguardLocked = true` or `isInteractive = false`) does not trigger `ACTION_AUTO_PAUSE` and that the foreground notification is built with `VISIBILITY_PUBLIC` and live countdown actions.
- Run `compile_applet` to verify the full Android build succeeds.
