# Implementation Plan: Fix Lock-Screen Battery Saver Exit & Style-Specific Pomodoro Minute Artworks

## 1. Fix Battery Saver (`FocusModeScreen`) Exit Bug After Unlocking

### Root Causes Identified
1. **Unconsumed Lock-Screen Navigation Request (`MainActivity` & `AppNavGraph`)**:
   - When the phone is locked while a Pomodoro is active, `MainActivity.onStart()` sets `navRequest.value = NavRequest(tab = TopLevel.FOCUS, openFocusMode = true)` to automatically open `FocusModeScreen` on the lock screen.
   - Because `navRequest.value` was never cleared after being handled by `MainShell`, unlocking the phone and tapping the `X` close button (`navController.popBackStack()`) returns to `MainShell`, whose `LaunchedEffect(navRequest)` re-runs with the stale `openFocusMode = true` request and immediately navigates right back into `FocusModeScreen`.
2. **Idle Burn-In Overlay Intercepting the First Tap (`FocusModeScreen`)**:
   - After 8 seconds on the lock screen, `FocusModeScreen` enters its `idle` dimmed state and places a full-screen `Box` overlay on top of the `TopBar` (`X` button). The first tap after unlocking was swallowed by that overlay just to set `idle = false`, requiring an extra tap to actually trigger `onExit()`.
3. **Style Preservation in `FocusModeScreen`**:
   - `FocusModeScreen` previously wrapped its content in `FocusIslandTheme(darkTheme = true)` without passing `style = LocalFocusStyle.current.id`, causing it to fall back to the default style instead of keeping the user's selected theme style.

### Solution
- **Consume `NavRequest` Immediately**: Add an `onNavRequestHandled: () -> Unit` callback from `AppNavGraph` / `MainShell` back to `MainActivity` so `navRequest.value = null` as soon as navigation completes.
- **Single-Trigger Lock Screen Entry**: Track lock-state transitions in `MainActivity` so `openFocusMode = true` is only dispatched when the device is actually locked, and automatically wake `FocusModeScreen` out of `idle` mode when the user unlocks the device (`Intent.ACTION_USER_PRESENT`).
- **Immediate Single-Tap `X` Button Response**: Remove the tap-blocking overlay over the top bar in `FocusModeScreen` so tapping the `X` button always exits Battery Saver / Focus Mode on the very first tap, even if the screen was dimmed.
- **Preserve Active Style in Battery Saver Mode**: Pass `style = LocalFocusStyle.current.id` into `FocusIslandTheme` inside `FocusModeScreen`.

---

## 2. Style-Specific Minute-by-Minute Pomodoro Background Art

Replace the generic dispersed stars in `PhaseTexturedBackground` (`Theme.kt`) and `FocusModeScreen` (`FocusModeScreen.kt`) with four bespoke, style-aware Canvas artworks rendered over a clean dark or light screen. Every artwork uses the current Pomodoro phase color (**Focus**, **Short Break / Courte pause**, **Long Break / Longue pause**) and reveals/grows one new element with each Pomodoro minute passed (with a smooth fade/extension during the active minute):

### 1. Night Arcade (`AppStyle.ARCADE`) — Motherboard Circuit Traces
- **Visual Design**: High-tech PCB motherboard traces radiating outward from the central Pomodoro timer circle.
- **Minute-by-Minute Progression**:
  - Each completed minute activates and extends a structured circuit trace from the outer ring of the timer circle outward across the screen using crisp `45°` and `90°` orthogonal bends.
  - Traces feature glowing microprocessor pin pads at the circle perimeter, fine parallel bus lines, and circular solder vias/nodes at trace endpoints.
  - The currently active minute's trace progressively draws outward along its path as the minute elapses.

### 2. Blossom (`AppStyle.BLOSSOM`) — Top-Canopy Blooming Flowers
- **Visual Design**: Graceful hanging sakura/blossom branches and layered 5-petal flowers blooming along the top of the screen.
- **Minute-by-Minute Progression**:
  - Subtle organic canopy branches drape softly from the top edge and upper corners.
  - With every Pomodoro minute passed, a new blossom flower (with delicate notched petals, glowing pistils, and occasional drifting petals/buds) blooms along the top canopy from left to right.

### 3. Lagoon (`AppStyle.LAGOON`) — Concentric Water Ripples & Rising Lagoon Bubbles
- **Visual Design**: Serene aquatic rings expanding from the timer circle paired with rising translucent lagoon bubbles.
- **Minute-by-Minute Progression**:
  - Each Pomodoro minute adds a delicate concentric water-ripple ring and broken wave arc radiating outward from the central timer circle, accompanied by a cluster of rising lagoon bubbles with specular highlights drifting upward from the lower edges.

### 4. Little Explorer (`AppStyle.EXPLORER`) — Treasure-Map Trail, Compass Rings & Islands
- **Visual Design**: Playful cartographic adventure background featuring compass-rose rings around the timer circle and a winding dashed treasure trail connecting tiny charted islands.
- **Minute-by-Minute Progression**:
  - Fine compass degree ticks frame the central timer circle.
  - Each Pomodoro minute extends a winding dashed map trail across the background and reveals new stepping-stone waypoints, miniature contoured islands, and destination markers.

---

## 3. Verification
- Compile the project with `compile_applet` to verify zero build or type errors.
- Verify that locking and unlocking the phone allows exiting `FocusModeScreen` with a single tap on the `X` button and that all 4 styles (`Night Arcade`, `Blossom`, `Lagoon`, `Little Explorer`) render their distinct progressive phase-colored artwork on both light and dark screens.
