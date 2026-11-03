# Permitted Apps & Crash-Proof Launch Guard for Lock Phone Challenge + Mandatory Account Sign-In

Enable users to select permitted applications (such as Phone, Calculator, or study tools) for Lock Phone challenges, remember their selection for future sessions, launch permitted apps from a compact icon row below the motivational quote with a strict single-flight debounce and crash-proof guard so rapid multi-taps can never crash the app or escape lock mode, and remove the "Continue without an account" guest access option from the Authentication screen.

## User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed user preferences from Phase 1 & Plan Revision:
> - **Confirmed Decision 1 (Lock Screen Presentation)**: Display permitted apps as a **compact app icon row below the motivational quote**, and transform the full-screen shield into a **compact floating Return pill** (showing the live countdown timer) while the user is inside a permitted app.
> - **Confirmed Decision 2 (Default Persistence)**: **Remember the user's selected permitted apps** across future Lock Phone sessions so they are pre-selected by default next time.
> - **Confirmed Decision 3 (Anti-Crash / Anti-Bypass Protection)**: Enforce an atomic single-flight cooldown (`1500ms`), safe screen-unpinning before external intent launch, dialer/call intent fallbacks, and defensive `WindowManager` + uncaught-exception guards so spamming the Phone app icon can never crash the process or exit lock mode.
> - **Confirmed Decision 4 (Account Required — Remove Guest Mode)**: **Remove the "Continue without an account" option** (`auth_guest` button, divider, and guest note) from `AuthScreen` so users must sign in or create an account to access the app.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. During Lock Phone challenge setup, users can pick which installed apps are permitted while the phone is locked (with their selection saved as the default for future Lock Phone challenges).
  2. During an active Lock Phone session, if permitted apps were selected, a clean row of circular/rounded app icons appears below the motivational quote card on both the in-app lock screen and the system overlay shield.
  3. Tapping a permitted app icon safely transitions the full-screen lock shield into a compact floating Return pill at the top of the screen and opens the permitted app. If the user switches to the Home screen, Recent Apps, or any non-permitted app, the full-screen lock shield immediately snaps back over the screen.
  4. On the Authentication welcome screen (`AuthScreen`), the "Continue without an account" button and note are removed so all users authenticate with an account (Sign In, Create Account, or Google Sign-In).
- **Target Audience / Persona**: Students and focused professionals who want a strict phone lock without losing access to essential utilities like the Phone dialer or calculator, while ensuring zero loopholes, crash exploits, or unauthenticated bypasses.
- **Key Value**: Combines practical access to essential apps with rock-solid stability that eliminates the classic "rapid-tap Phone icon crash" exploit found in other detox apps, alongside mandatory account authentication.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Selecting Permitted Apps (New Challenge Sheet — Lock Phone Mode)**:
     - Below the duration/until-hour selector, a **"Permitted Apps"** card shows currently selected app chips/icons (pre-populated from the user's saved default selection) and a **"Choose apps"** button.
     - Tapping **"Choose apps"** opens a searchable multi-select sheet listing launchable installed apps (plus a dedicated Phone/Dialer entry if the device's default dialer uses `ACTION_DIAL`) with checkboxes and a **"Save"** button.
  2. **Using Permitted Apps While Locked**:
     - On the active Lock Phone screen, the circular countdown clock and motivational quote card remain centered, with a **compact horizontal row of permitted app icons + labels** directly beneath the quote card.
     - Tapping an icon once opens that app smoothly. Rapidly tapping the icon 10+ times is debounced at the atomic level so only the first tap executes.
  3. **Floating Return Pill & Auto-Relock**:
     - While inside a permitted app, a compact pill at the top edge displays the live countdown (`🔒 44:12 · Return`) so the user can return to the lock screen with one tap, or automatically get brought back to the full-screen lock shield the moment they leave the permitted app or when the timer reaches `00:00`.
  4. **Mandatory Account Sign-In (`AuthScreen`)**:
     - The welcome/authentication screen presents the Sign In / Create Account tabs, email/password form, and Google Sign-In button, without the "Continue without an account" button or guest mode note below.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Calm, distraction-free Material 3 surfaces (`surfaceContainerHigh` cards, `primary` accents) matching Focus Island's Lagoon/Dark theme.
  - *Component Styling & Layout*: 48dp+ touch targets on permitted app buttons with subtle ripple feedback, circular/rounded-square app icons (`36.dp`), and single-line truncated labels.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Why Detox Apps Crash on Rapid Phone App Clicks & How We Prevent It**
  - *Chosen Approach*: Four-layer defense:
    1. **Atomic Single-Flight Debounce (`1500ms`)**: An `AtomicBoolean` + `AtomicLong` gate in `PhoneLockOverlayManager` drops all repeated taps within `1500ms` before any `Intent`, `LockTask`, or `WindowManager` call occurs.
    2. **Clean Pinning Transition**: Standard Android Screen Pinning (`startLockTask`) blocks external package launches if still pinned. Before launching a permitted app, we cleanly release `LockTask` on the main thread inside `runCatching`, switch the `WindowManager` overlay into compact floating-pill mode, and launch the app with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP`.
    3. **Dialer / Phone Fallback Resolution**: If the user selects the Phone app, we resolve `packageManager.getLaunchIntentForPackage(pkg)` with an automatic fallback to `Intent(Intent.ACTION_DIAL)` (and whitelist associated dialer/in-call system packages in `AppLaunchMonitor`) so opening the Phone app never throws `ActivityNotFoundException` or `NullPointerException`, nor gets re-blocked mid-transition.
    4. **Idempotent `WindowManager` & Process Guard**: All overlay view mutations verify `isAttachedToWindow` on the main thread inside `runCatching`, and an active-lock uncaught-exception safety net prevents an unexpected UI exception from terminating the process.
  - *Why*: Guarantees that spamming any permitted app button cannot crash the app or bypass the lock timer.
- **Decision 2: Persisting Default Permitted Apps**
  - *Chosen Approach*: Store `defaultAllowedPackages` in `ChallengeStore` preferences alongside `allowedPackages` on the active `Challenge` model.
  - *Why*: Ensures the user's preferred essential apps (e.g., Phone) are automatically remembered for their next Lock Phone session while also staying locked to the active session once started.
- **Decision 3: Removing "Continue Without an Account"**
  - *Chosen Approach*: Remove the guest access button (`auth_guest`), its preceding `OrDivider`, and the `auth_guest_note` caption from `AuthScreen`, and ensure existing guest session flags are cleared if unauthenticated so users always sign in with an account.
  - *Why*: Keeps study progress, group challenges, and profile data consistently tied to a real user account.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                     NewChallengeSheet (Lock Phone Mode)                   │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Permitted Apps Selector Card + Searchable App Picker Dialog/Sheet   │  │
│  │ (Loads & saves defaultAllowedPackages in ChallengeStore)            │  │
│  └──────────────────────────────────┬──────────────────────────────────┘  │
└─────────────────────────────────────┼─────────────────────────────────────┘
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                         ChallengeStore & Challenge                        │
│  • Challenge.allowedPackages: Set<String>                                 │
│  • ChallengeStore.getDefaultAllowedPackages() / setDefaultAllowedPackages │
└─────────────────┬───────────────────────────────────────┬─────────────────┘
                  ▼                                       ▼
┌───────────────────────────────────────┐ ┌─────────────────────────────────┐
│       StrictPhoneLockOverlay          │ │    PhoneLockOverlayManager      │
│  • Countdown Ring + Quote Card        │ │  • Full-Screen Shield Mode      │
│  • Compact Permitted Apps Row         │ │  • Compact Floating Pill Mode   │
│  • Calls launchPermittedAppSafely()   │ │    (while in permitted app)     │
└─────────────────┬─────────────────────┘ └───────────────┬─────────────────┘
                  └───────────────────┬───────────────────┘
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│         Crash-Proof Single-Flight App Launcher & AppLaunchMonitor         │
│  • Atomic 1500ms debounce + main-thread runCatching                       │
│  • Unpins LockTask -> switches shield to Floating Pill -> launches Intent │
│  • Dialer/Call package whitelisting + 3.5s transition grace window        │
│  • Instant full-screen re-lock when leaving permitted app or time is up   │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **`Challenge`**: Adds `val allowedPackages: Set<String> = emptySet()`, serialized as a JSON array in `ChallengeStore` while the Lock Phone session is active.
- **`ChallengeStore`**: Adds `getDefaultAllowedPackages(): Set<String>` and `setDefaultAllowedPackages(packages: Set<String>)` backed by SharedPreferences (`KEY_DEFAULT_ALLOWED_PACKAGES`) so the user's selection is remembered across sessions.
- **`AuthScreen` / `AuthRepository`**: Removes the "Continue without an account" UI section from `AuthContent` in `AuthScreen.kt` so only authenticated flows (Email/Password Sign In, Sign Up, and Google Sign-In) are offered.

### Interactive Component & State Mapping
1. **Permitted Apps Picker (`NewChallengeSheet` & `PhoneLockComponents`)**:
   - Queries launchable packages via `PackageManager.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), MATCH_ALL)` off the main thread, plus ensures the default system Dialer (`TelecomManager.defaultDialerPackage` / `Intent(Intent.ACTION_DIAL)`) is always available and launchable.
   - Selecting/deselecting apps updates both the current `PendingChallenge.allowedPackages` and `ChallengeStore.setDefaultAllowedPackages(...)`.
2. **Compact Permitted Apps Row (`StrictPhoneLockOverlay` & `PhoneLockOverlayManager`)**:
   - Renders only when `challenge.allowedPackages.isNotEmpty()`.
   - Tapping any app icon invokes `PhoneLockOverlayManager.launchPermittedAppSafely(context, pkg)`.
3. **Transition Between Full-Screen Lock & Permitted App (`PhoneLockOverlayManager` & `AppLaunchMonitor`)**:
   - `launchPermittedAppSafely` checks the atomic 1500ms cooldown, sets a brief transition grace window, switches the overlay to the top floating Return pill, unpins `LockTask`, and starts the target package (or `ACTION_DIAL` fallback).
   - `AppLaunchMonitor` checks `foregroundPackage()` every `120ms`: if the foreground package is permitted (or within the launch transition grace window), the floating Return pill remains; if the user navigates to any non-permitted app or taps the floating Return pill, `PhoneLockOverlayManager.reLockImmediately(context)` restores the full-screen lock shield immediately.
4. **Authentication Screen (`AuthScreen.kt`)**:
   - Removes the `OutlinedButton` for `R.string.auth_guest` and `R.string.auth_guest_note` from `AuthContent`.
