# FSRS-5 Spaced Repetition Scheduler Replacement & Data Migration

Replace the legacy SM-2 spaced repetition algorithm with a mathematical **FSRS-5 (Free Spaced Repetition Scheduler)** engine targeting 90% retention, update the flashcard persistence model to store FSRS memory parameters (`stability`, `difficulty`, `retrievability`, and `state`) in place of `easeFactor`, and seamlessly migrate all existing study progress.

## User Review & Critical Decisions

> [!IMPORTANT]
> All changes are strictly isolated to the spaced repetition scheduling logic, card data schema, and storage migration layer. No UI screens, navigation flows, file structures, or unrelated features will be altered.

- **Confirmed Decision 1 — FSRS-5 with 90% Target Retention**: Uses the official 19-parameter FSRS-5 weight vector ($w_0 \dots w_{18}$) with power-law forgetting curve decay ($d = -0.5$, factor $f = 19/81$) and desired retention $r = 0.90$.
- **Confirmed Decision 2 — Hybrid Intra-Day Learning Steps + Day-Level FSRS Intervals**: Cards graded **Again** or **Hard** during initial learning or after a lapse use short intra-day minute steps (`LEARNING` / `RELEARNING` states) while updating FSRS memory parameters, and graduate to multi-day FSRS-5 intervals (`REVIEW` state) on **Good** or **Easy**.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades flashcard review scheduling from the static SM-2 multiplier formula to the Three-Component Model of Memory (DSR: Difficulty, Stability, Retrievability) used by FSRS-5.
- **Target Audience / Persona**: Learners reviewing flashcards across decks who need accurate, adaptive review intervals based on actual elapsed time and memory decay rather than rigid ease-factor multipliers.
- **Key Value**: Prevents "ease hell" from SM-2, accounts for early or overdue reviews via real-time retrievability calculation, and preserves 100% of existing card history through automatic SM-2-to-FSRS data migration.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Seamless Upgrade on Launch**: Existing flashcards in the local database (and any imported JSON backups) are automatically migrated from SM-2 (`easeFactor`, `intervalDays`, `repetitions`) to FSRS-5 (`stability`, `difficulty`, `retrievability`, `state`) without user intervention.
  2. **Review Session Grading**: When flipping a flashcard during a study session, the four existing grade buttons (**Again**, **Hard**, **Good**, **Easy**) preview the exact next interval computed by FSRS-5 and update the card's DSR state upon selection.
- **Visual Identity & Theme**:
  - Strictly preserves the existing UI, layout, typography, color tokens, and navigation structure with zero visual changes as requested.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Pure Kotlin FSRS-5 Mathematical Engine**
  - *Chosen Approach*: Implement the complete FSRS-5 equations directly in the existing scheduler module using the standard 19-weight parameter set ($w_0 \dots w_{18}$).
  - *Why*: Eliminates external native/JNI dependency bloat, guarantees deterministic offline execution, and allows real-time interval previews on the review buttons.
  - *Alternatives Considered*: External JVM/C++ wrappers, which add unnecessary binary size and require structural changes.
- **Decision 2: Non-Destructive SM-2 to FSRS-5 Migration**
  - *Chosen Approach*: Convert existing SM-2 cards both at the SQLite database migration level (`v8 -> v9`) and through a Kotlin conversion function for legacy JSON backups.
  - *Why*: Ensures neither active database records nor older exported backup files lose review history when `easeFactor` is removed from the schema.
  - *Mapping Formula*:
    - **State**: Cards with `repetitions == 0` and `intervalDays == 0` become `NEW`; cards with `intervalDays > 0` become `REVIEW`; cards in intra-day steps become `LEARNING`.
    - **Difficulty ($D \in [1, 10]$)**: Inversely mapped from SM-2 `easeFactor` ($\text{EF} \in [1.3, 3.0]$) via $D = \text{clamp}\left(10.0 - \frac{\text{EF} - 1.3}{1.7} \times 7.0,\; 1.0,\; 10.0\right)$, so default $\text{EF} = 2.5$ maps to $D \approx 5.06$.
    - **Stability ($S$)**: Initialized from the card's current `intervalDays` (since $I(0.90, S) = S$ under 90% target retention) or initial FSRS stability for first-step cards.
    - **Retrievability ($R \in [0, 1]$)**: Computed from elapsed days since `lastReviewedAt` using $R(t, S) = \left(1 + \frac{19}{81} \cdot \frac{t}{S}\right)^{-0.5}$.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                        Review Session & ViewModel                       │
│  • Queries due flashcards & previews next interval per ReviewGrade      │
│  • Dispatches selected grade (AGAIN, HARD, GOOD, EASY)                  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         FSRS-5 Scheduling Engine                        │
│  • Elapsed time t = max(0, (now - lastReviewedAt) / 86_400_000)         │
│  • Retrievability R(t, S) = (1 + (19/81) * (t / S))^(-0.5)              │
│  • Initial S0(G), D0(G) for NEW cards                                   │
│  • Short-term stability S'(S, G) for same-day LEARNING / RELEARNING     │
│  • Mean-reverting Difficulty D_next & Recall/Forget Stability S_next    │
│  • Next interval I(r=0.90, S_next) clamped to [1, maxIntervalDays]      │
└─────────────────┬─────────────────────────────────────┬─────────────────┘
                  │                                     │
                  ▼                                     ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│     Flashcard Entity & Storage    │ │    SM-2 -> FSRS-5 Migration       │
│  • Removed: easeFactor            │ │  • SQLite v8 -> v9 table rebuild  │
│  • Added: stability (Float)       │ │  • Converts EF, intervalDays,     │
│  • Added: difficulty (Float)      │ │    repetitions -> S, D, R, state  │
│  • Added: retrievability (Float)  │ │  • Legacy backup JSON converter   │
│  • Added: state (String)          │ │    for backward compatibility     │
└───────────────────────────────────┘ └───────────────────────────────────┘
```

### Data Model & State

- **Flashcard Data Model**:
  - Removes `easeFactor: Float`.
  - Adds:
    - `stability: Float = 0f` — Memory stability $S$ in days (the interval at which retrievability drops to 90%).
    - `difficulty: Float = 0f` — Memory difficulty $D \in [1.0, 10.0]$ (`0f` when `state == "NEW"`).
    - `retrievability: Float = 1f` — Estimated probability of recall $R \in [0.0, 1.0]$ at the time of scheduling.
    - `state: String = "NEW"` — Lifecycle state (`NEW`, `LEARNING`, `REVIEW`, `RELEARNING`).
  - Retains `intervalDays`, `repetitions`, `dueDate`, and `lastReviewedAt` so existing schedule summaries and due-card queries work with zero UI changes.

### Interactive Component & State Mapping

- **New Card First Review (`state == "NEW"`)**:
  - Computes initial stability $S_0(G) = w_{G-1}$ and initial difficulty $D_0(G) = \text{clamp}(w_4 - e^{w_5(G-1)} + 1, 1, 10)$.
  - **Again ($G=1$)** / **Hard ($G=2$)**: Transitions to `LEARNING` with short intra-day due offsets (1 minute for Again, 6 minutes for Hard) and `intervalDays = 0`.
  - **Good ($G=3$)** / **Easy ($G=4$)**: Transitions directly to `REVIEW` and schedules day-level intervals derived from $S_0(G)$.
- **Learning / Relearning Review (`state == "LEARNING" || state == "RELEARNING"`)**:
  - Updates difficulty $D_{\text{next}}$ and short-term stability $S'(S, G) = S \cdot e^{w_{17}(G - 3 + w_{18})}$.
  - **Again ($G=1$)** / **Hard ($G=2$)**: Remains in `LEARNING` / `RELEARNING` with intra-day minute intervals (1 min / 10 min or configured relearning step).
  - **Good ($G=3$)** / **Easy ($G=4$)**: Graduates to `REVIEW` with a multi-day interval $I(0.90, S')$.
- **Graduated Card Review (`state == "REVIEW"`)**:
  - Calculates elapsed days $t$ and current retrievability $R(t, S) = (1 + \frac{19}{81} \frac{t}{S})^{-0.5}$.
  - Updates difficulty $D_{\text{next}}$ using FSRS-5 linear damping and mean reversion toward $D_0(4)$.
  - **Again ($G=1$)**: Computes post-lapse stability $S'_f(D, S, R)$, transitions to `RELEARNING`, and schedules the intra-day relearning step (`intervalDays = 0`).
  - **Hard ($G=2$)**, **Good ($G=3$)**, **Easy ($G=4$)**: Computes recall stability $S'_r(D, S, R, G)$ (applying hard penalty $w_{15}$ for Hard and easy bonus $w_{16}$ for Easy), remains in `REVIEW`, and enforces monotonic day intervals ($1 \le I_{\text{Hard}} \le I_{\text{Good}} < I_{\text{Easy}} \le \text{maxIntervalDays}$).
