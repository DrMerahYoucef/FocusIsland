# Unbreakable Phone Lock: System Overlay Shield & Screen Pinning

This update upgrades the **Lock Phone** personal challenge from an Activity-only screen to a dual-layer **Full-Screen System Window Overlay (`TYPE_APPLICATION_OVERLAY`) + Android Screen Pinning (`startLockTask`)** lockdown so users cannot escape via the Recent Apps gesture, Home gesture, or Notification Drawer.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions incorporate your answers from our clarification step:

- **Confirmed Decision 1 — System Overlay Shield + Android Screen Pinning**: We will combine a persistent, full-screen `WindowManager` system overlay (`TYPE_APPLICATION_OVERLAY`) with Android's native Screen Pinning (`startLockTask()`). The system overlay sits above Recent Apps, the Home launcher, and any app opened from the notification shade, while Screen Pinning disables the Notification Drawer and Recent Apps gesture at the OS level.
- **Confirmed Decision 2 — Instant Re-Lock on Exiting Allowed Apps (No Floating Pill)**: When you tap a pre-selected necessary app on the lock screen, the full-screen shield hides cleanly (with no floating pill) while that whitelisted app stays in the foreground, and snaps back over the screen in under 150ms the moment you swipe to Recent Apps, go Home, or switch to any other app.

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates the two Android Activity loopholes—swiping up to Recent Apps and swiping down the Notification Drawer to launch another app—by rendering the Lock Phone screen inside a true `WindowManager` system overlay window (`TYPE_APPLICATION_OVERLAY`) paired with Android Screen Pinning (`startLockTask`).
- **Target Audience / Persona**: Users who want a zero-loophole phone lock where swiping to Recent Apps, pressing Home, or clicking items in the Notification Drawer never lets them use the phone until the countdown reaches `00:00`.
- **Key Value**: Unlike regular Android Activities (which are subject to Recents gestures and Android's 5-second background Activity launch delay after pressing Home), a `WindowManager` system overlay stays pinned above the Home screen, Recent Apps carousel, and any app launched from the notification shade at all times.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Starting Lock Phone**:
   - As soon as you tap **Lock Phone Now**, two lockdown layers activate simultaneously:
     1. **System Overlay Shield (`PhoneLockOverlayManager`)**: Attaches a full-screen, touch-blocking `TYPE_APPLICATION_OVERLAY` window directly to the Android `WindowManager` (plus a top-edge gesture blocker) that covers the entire display, including over Recent Apps, Home, and any app launched from a notification.
     2. **Android Screen Pinning (`startLockTask`)**: Triggers Android's native Screen Pinning on the lock Activity so the OS disables the Notification Drawer pulldown, Quick Settings, and Recent Apps gesture. An inline status pill on the lock screen also lets you re-activate Screen Pinning with one tap if unpinned.
2. **Blocking Notification Drawer & Recent Apps**:
   - If you swipe up to **Recent Apps** or press **Home**, the `TYPE_APPLICATION_OVERLAY` shield remains 100% visible on top of the screen—Recent Apps and the Home launcher are completely hidden underneath and cannot be tapped.
   - If you pull down the **Notification Drawer** (when not screen-pinned) and tap a notification, Settings icon, or Quick Settings tile, the underlying Activity is still completely covered by the full-screen `TYPE_APPLICATION_OVERLAY` shield, and all non-whitelisted notifications are immediately cancelled by `FocusNotificationListenerService` + Do Not Disturb.
3. **Using Allowed Necessary Apps & Instant Re-Lock**:
   - Tapping an allowed necessary app icon on the lock shield launches that app and hides the shield cleanly (no floating pill) while that allowed package remains in the foreground.
   - The instant you leave the allowed app (swipe to Recents, press Home, or switch to any non-whitelisted package), the `WindowManager` shield re-appears immediately with zero OS background-launch delay.
4. **Automatic Unlock at `00:00`**:
   - When the countdown reaches `00:00`, the app automatically exits Screen Pinning (`stopLockTask()`), removes the `WindowManager` system overlay shield, records the challenge as **Done**, and restores normal phone access.

### Visual Identity & Theme

- **Aesthetic Direction**: High-contrast, distraction-free sanctuary surface (`MaterialTheme.colorScheme.background` with `surfaceContainerHigh` cards) that fills 100% of the physical display (`FLAG_LAYOUT_IN_SCREEN` + `FLAG_LAYOUT_NO_LIMITS`).
- **Interactive Feedback**:
  - **Strict Lock Header**: Displays the lock badge, unlock timestamp, and a **Screen Pinning Active / Tap to Pin Screen** security chip so you can lock out the notification shade at the OS level.
  - **Centerpiece**: Live `PhaseRing` + `RingClock` countdown and the single session motivational quote card.
  - **Bottom Dock**: Interactive shortcuts for pre-selected necessary apps.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — `WindowManager` System Overlay (`TYPE_APPLICATION_OVERLAY`) Instead of Activity-Only Lock**:
  - *Chosen Approach*: Render the Lock Phone screen in a dedicated `WindowManager` `TYPE_APPLICATION_OVERLAY` window managed by `PhoneLockOverlayManager` inside `PomodoroTimerService`, backed by `MainActivity` / `FocusOverlayActivity` with `startLockTask()`.
  - *Why*: Android 10+ blocks background `startActivity()` calls for 5 seconds after the user presses Home or opens Recent Apps. A `WindowManager` overlay is a persistent system window that is never placed into the Recent Apps carousel and can be shown/hidden in `<16ms` without `startActivity()`.
  - *Alternatives Considered*: Relying solely on `FocusOverlayActivity`—rejected because swiping to Recents or pressing Home hides Activities and triggers Android's 5-second background launch block.
- **Decision 2 — Combining `startLockTask()` (Screen Pinning) with System Overlay**:
  - *Chosen Approach*: Automatically invoke `Activity.startLockTask()` when Lock Phone starts (and provide a one-tap pin button on the lock screen), while keeping the `TYPE_APPLICATION_OVERLAY` shield active.
  - *Why*: `startLockTask()` is the only non-Device-Owner Android API that completely prevents pulling down the Notification Drawer and Quick Settings at the OS level, while `TYPE_APPLICATION_OVERLAY` guarantees that even if Screen Pinning is not active (or while switching to an allowed app), the user still cannot use any non-whitelisted app, Home, or Recents.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     PomodoroTimerService (Foreground)                   │
│  ┌──────────────────────────────┐   ┌────────────────────────────────┐  │
│  │      AppLaunchMonitor        │   │    PhoneLockOverlayManager     │  │
│  │ • Polls foreground pkg 120ms │──▶│ • Full-screen WindowManager    │  │
│  │ • Detects Home / Recents /   │   │   TYPE_APPLICATION_OVERLAY     │  │
│  │   non-allowed app instantly  │   │ • Covers Recents, Home & Apps  │  │
│  └──────────────────────────────┘   │ • Hides only while whitelisted │  │
│                                     │   app is actively in foreground│  │
│                                     └───────────────┬────────────────┘  │
└─────────────────────────────────────────────────────┼───────────────────┘
                                                      │
                                                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│             MainActivity / FocusOverlayActivity (Lock Host)             │
│  • Invokes Activity.startLockTask() (Android Screen Pinning)            │
│    to disable Notification Shade, Quick Settings & Recents at OS level  │
│  • Calls stopLockTask() automatically when countdown reaches 00:00      │
└─────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

1. **`PhoneLockOverlayManager` (System Window Shield)**:
   - Creates a full-screen `WindowManager` view (`TYPE_APPLICATION_OVERLAY`, `MATCH_PARENT` × `MATCH_PARENT`, `FLAG_LAYOUT_IN_SCREEN | FLAG_FULLSCREEN | FLAG_KEEP_SCREEN_ON`) that hosts the live countdown clock, session motivational quote, Screen Pinning trigger, and allowed app shortcuts.
   - Intercepts all touches and hardware Back keys (`KEYCODE_BACK`) across the entire screen so nothing behind the overlay can be clicked—even if opened from the notification drawer or Recent Apps.
   - Exposes `onAllowedAppLaunched(pkg)`: temporarily hides the full-screen overlay while the user interacts with that allowed app (with no floating pill).
2. **`AppLaunchMonitor` Fast Re-Lock Loop (`120ms` polling during Lock Phone)**:
   - Continuously inspects the foreground package and UsageEvents (`MOVE_TO_FOREGROUND`, `ACTIVITY_PAUSED`, `ACTIVITY_STOPPED`, and launcher/Recents packages such as `com.android.systemui` or default home launcher).
   - The moment the user exits an allowed app (to Home, Recents, Settings, or any non-allowed package), calls `PhoneLockOverlayManager.showShield()` on the main thread, restoring the full-screen lock overlay immediately without waiting for `startActivity()`.
3. **Android Screen Pinning (`startLockTask` / `stopLockTask`)**:
   - When a Lock Phone challenge starts or when the user taps the **Pin Screen (Block Notification Bar)** chip on the lock screen, `MainActivity` / `FocusOverlayActivity` calls `startLockTask()` if `ActivityManager.lockTaskModeState == LOCK_TASK_MODE_NONE`.
   - When the timer reaches `00:00`, `stopLockTask()` is called automatically alongside `PhoneLockOverlayManager.dismissShield()`.
