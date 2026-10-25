# Cycle-Reactive Textured Background & In-App Language Switcher

This update introduces a fine dot-grain textured background with a soft radial glow that dynamically adapts its palette to the active Pomodoro cycle (**Focus**, **Short Break**, or **Long Break**) across the app shell and Focus screens, and adds an instant **Language** selector (**System**, **English**, **Français**) in Settings.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design and behavior choices were confirmed from your answers:

- **Confirmed Decision 1 — Texture Scope**: The phase-colored fine texture background will appear across the entire app shell, secondary screens, and Focus screens so the atmosphere consistently reflects the current Pomodoro cycle.
- **Confirmed Decision 2 — Texture Style**: A subtle fine dot-grain pattern paired with a soft radial ambient glow that smoothly transitions between the Focus, Short Break, and Long Break colors.
- **Confirmed Decision 3 — Language Selector**: A 3-option segmented selector (**System**, **English**, **Français**) in Settings under *Appearance and privacy* that updates the app's language immediately and persists across launches.

## 1. Overview & Core Concept

- **What It Does**:
  1. **Cycle-Reactive Fine Texture Background**: Replaces flat background fills with a layered canvas that renders a soft radial glow and a fine staggered dot-grain texture tinted by the active Pomodoro phase (`FOCUS`, `SHORT_BREAK`, or `LONG_BREAK`).
  2. **Language Switcher in Settings**: Lets users choose between **System**, **English**, and **Français** directly inside Settings, immediately re-localizing all screens and strings.
- **Target Audience / Persona**: Students and focus enthusiasts who want tactile visual feedback tied to their current work/rest cycle and seamless bilingual (French/English) control.
- **Key Value**: Makes the current timer phase immediately perceptible anywhere in the app while giving users full control over the interface language regardless of their phone's OS language.

## 2. User Experience & Visual Design

- **Key User Flows**:
  - **Cycle-Following Texture**:
    1. During a **Focus** phase, the background renders a fine stippled dot-grain and soft upper radial glow using the active style's primary Focus color.
    2. When the timer transitions to **Short Break** or **Long Break** (or when the user skips phases), the radial glow and fine dot-grain smoothly cross-fade (`600ms` animation) into the Short Break (`secondary`) or Long Break (`rest`) accent color.
  - **Language Selection in Settings**:
    1. The user opens **Settings → Appearance and privacy** (`Réglages → Apparence et confidentialité`).
    2. Under **Language / Langue**, a 3-button segmented row offers **System / Système**, **English**, and **Français**.
    3. Tapping any option immediately switches all UI labels, plurals, and date/time formatting across the app and saves the preference.
- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Tactile, atmospheric minimalism—combining a delicate staggered dot-grain grid with a soft radial halo that respects both Light and Dark modes as well as all four app styles (*Lagoon*, *Night Arcade*, *Blossom*, *Little Explorer*).
  - *Color Palette & Mood*:
    - **Focus**: `MaterialTheme.colorScheme.primary`
    - **Short Break**: `MaterialTheme.colorScheme.secondary`
    - **Long Break**: `MaterialTheme.extendedColors.rest`
- **Interactive Feedback & Motion**:
  - Smooth `600ms` color interpolation when switching between Focus, Short Break, and Long Break phases.
  - Instantaneous Compose recomposition with the localized `Context` and `Configuration` when switching languages in Settings.

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Custom Canvas `drawBehind` for the Fine Dot-Grain Texture**
  - *Chosen Approach*: Render the radial glow and staggered micro-dot matrix procedurally via `Modifier.drawBehind` using the animated `phaseColor(timerState.phase)`.
  - *Why*: Resolution-independent across phones and tablets, zero bitmap memory overhead, and animates smoothly between theme styles, light/dark modes, and timer phases.
- **Decision 2: Reactive Locale Override + BaseContext Persistence**
  - *Chosen Approach*: Persist the selected language code (`system`, `en`, `fr`) in `SharedPreferences` and provide a localized `Context` and `Configuration` through `CompositionLocalProvider` at the root theme level (in addition to `attachBaseContext`).
  - *Why*: Updating `LocalContext` and `LocalConfiguration` reactively in Compose switches all `stringResource` and `pluralStringResource` calls immediately without jarring activity restarts or losing navigation state.

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         PomodoroTimerService.state                      │
│                  (Phase.FOCUS | SHORT_BREAK | LONG_BREAK)               │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       PhaseTexturedBackground                           │
│  • Animates phaseColor(phase) over 600ms                                │
│  • Draws soft radial phase glow + fine staggered dot-grain texture      │
│  • Wraps MainShell, SecondaryScreen, and FocusModeScreen                │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                 SettingsViewModel ("app_language" pref)                 │
│                  AppLanguage: SYSTEM | ENGLISH | FRENCH                 │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               Localized Context & Configuration Provider                │
│  • Overrides LocalConfiguration & LocalContext with chosen Locale       │
│  • Immediately updates all stringResource / pluralStringResource calls  │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `AppLanguage` enum (`SYSTEM("system")`, `ENGLISH("en")`, `FRENCH("fr")`) added to `SettingsState` and persisted in `focusflow_prefs` under `"app_language"`.
- **Interactive Component & State Mapping**:
  - `SettingsScreen`: Adds the Language `SingleChoiceSegmentedButtonRow` inside the Appearance group, calling `viewModel.updateAppLanguage(lang)`.
  - `PhaseTexturedBackground`: Observes `PomodoroTimerService.state` and paints the phase-tinted fine dot-grain and radial glow behind `MainShell` and `SecondaryScreen` (with transparent `Scaffold` containers so the texture remains visible everywhere).
