package com.example.data.srs

import com.example.data.db.entity.RevisionNoteEntity
import com.example.data.db.entity.SyncStatus
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ReviewGrade(val label: String, val rating: Int) {
    AGAIN("Encore", 1),
    HARD("Difficile", 2),
    GOOD("Bien", 3),
    EASY("Facile", 4)
}

enum class CardState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING;

    companion object {
        fun fromString(value: String?): CardState =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: NEW
    }
}

data class FsrsMemoryState(
    val stability: Float,
    val difficulty: Float,
    val retrievability: Float,
    val state: String,
    val intervalDays: Int,
    val repetitions: Int
)

/**
 * FSRS-5 (Free Spaced Repetition Scheduler) implementation with 90% target retention
 * and short intra-day learning/relearning steps for Again and Hard grades.
 */
object FsrsScheduler {

    /** Official FSRS-5 default 19-parameter weight vector (w0..w18). */
    val DEFAULT_WEIGHTS = doubleArrayOf(
        0.40255,  // w0: initial stability for Again
        1.18385,  // w1: initial stability for Hard
        3.17300,  // w2: initial stability for Good
        15.69105, // w3: initial stability for Easy
        7.19490,  // w4: initial difficulty base
        0.53450,  // w5: initial difficulty exponent
        1.46040,  // w6: difficulty delta weight
        0.00460,  // w7: mean reversion weight toward D0(4)
        1.54575,  // w8: recall stability exponent
        0.11920,  // w9: recall stability saturation power
        1.01925,  // w10: recall stability retrievability factor
        1.93950,  // w11: forget stability base
        0.11000,  // w12: forget stability difficulty power
        0.29605,  // w13: forget stability prior stability power
        2.26980,  // w14: forget stability retrievability factor
        0.23150,  // w15: Hard recall penalty
        2.98980,  // w16: Easy recall bonus
        0.51655,  // w17: short-term stability exponent
        0.66210   // w18: short-term stability offset
    )

    const val DESIRED_RETENTION = 0.90
    const val MAX_INTERVAL_DAYS = 36500
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0 // 0.9^(-1/DECAY) - 1
    private const val MILLIS_PER_DAY = 86_400_000.0

    // Short intra-day steps (minutes) for NEW / LEARNING / RELEARNING cards on Again and Hard
    private const val LEARNING_AGAIN_MINUTES = 1L
    private const val LEARNING_HARD_MINUTES = 6L
    private const val LEARNING_STEP2_HARD_MINUTES = 10L
    private const val RELEARNING_AGAIN_MINUTES = 10L
    private const val RELEARNING_HARD_MINUTES = 15L

    /**
     * Power-law forgetting curve: R(t, S) = (1 + FACTOR * t / S)^DECAY.
     * Returns probability of recall in [0.0, 1.0].
     */
    fun computeRetrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        if (elapsedDays <= 0.0) return 1.0
        return (1.0 + FACTOR * (elapsedDays / stability)).pow(DECAY).coerceIn(0.0, 1.0)
    }

    /**
     * Initial stability S0(G) = w[G - 1] for first review grade G in {1, 2, 3, 4}.
     */
    fun initialStability(grade: ReviewGrade, w: DoubleArray = DEFAULT_WEIGHTS): Double {
        return w[grade.rating - 1].coerceAtLeast(0.01)
    }

    /**
     * FSRS-5 initial difficulty: D0(G) = clamp(w4 - exp(w5 * (G - 1)) + 1, 1, 10).
     */
    fun initialDifficulty(grade: ReviewGrade, w: DoubleArray = DEFAULT_WEIGHTS): Double {
        val g = grade.rating.toDouble()
        val d0 = w[4] - exp(w[5] * (g - 1.0)) + 1.0
        return d0.coerceIn(1.0, 10.0)
    }

    /**
     * FSRS-5 short-term stability update for same-day / learning reviews:
     * S'(S, G) = S * exp(w17 * (G - 3 + w18)).
     */
    fun nextShortTermStability(stability: Double, grade: ReviewGrade, w: DoubleArray = DEFAULT_WEIGHTS): Double {
        val baseS = if (stability > 0.0) stability else initialStability(grade, w)
        val g = grade.rating.toDouble()
        return (baseS * exp(w[17] * (g - 3.0 + w[18]))).coerceIn(0.01, MAX_INTERVAL_DAYS.toDouble())
    }

    /**
     * FSRS-5 difficulty update with linear damping and mean reversion toward D0(EASY):
     * deltaD = -w6 * (G - 3)
     * D' = D + deltaD * ((10 - D) / 9)
     * D_next = clamp(w7 * D0(4) + (1 - w7) * D', 1, 10)
     */
    fun nextDifficulty(difficulty: Double, grade: ReviewGrade, w: DoubleArray = DEFAULT_WEIGHTS): Double {
        val currentD = if (difficulty in 1.0..10.0) difficulty else initialDifficulty(grade, w)
        val g = grade.rating.toDouble()
        val deltaD = -w[6] * (g - 3.0)
        val dampedD = currentD + deltaD * ((10.0 - currentD) / 9.0)
        val d0Easy = initialDifficulty(ReviewGrade.EASY, w)
        val revertedD = w[7] * d0Easy + (1.0 - w[7]) * dampedD
        return revertedD.coerceIn(1.0, 10.0)
    }

    /**
     * FSRS-5 recall stability after successful review (Hard, Good, Easy):
     * S'_r(D, S, R, G) = S * (1 + exp(w8) * (11 - D) * S^(-w9) * (exp(w10 * (1 - R)) - 1) * hardPenalty * easyBonus)
     */
    fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        grade: ReviewGrade,
        w: DoubleArray = DEFAULT_WEIGHTS
    ): Double {
        val s = stability.coerceAtLeast(0.01)
        val d = difficulty.coerceIn(1.0, 10.0)
        val r = retrievability.coerceIn(0.0, 1.0)
        val hardPenalty = if (grade == ReviewGrade.HARD) w[15] else 1.0
        val easyBonus = if (grade == ReviewGrade.EASY) w[16] else 1.0
        val increment = exp(w[8]) *
            (11.0 - d) *
            s.pow(-w[9]) *
            (exp(w[10] * (1.0 - r)) - 1.0) *
            hardPenalty *
            easyBonus
        return (s * (1.0 + increment)).coerceIn(0.01, MAX_INTERVAL_DAYS.toDouble())
    }

    /**
     * FSRS-5 post-lapse (forget) stability on Again (G = 1):
     * S'_f(D, S, R) = min(w11 * D^(-w12) * ((S + 1)^w13 - 1) * exp(w14 * (1 - R)), S)
     */
    fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        w: DoubleArray = DEFAULT_WEIGHTS
    ): Double {
        val s = stability.coerceAtLeast(0.01)
        val d = difficulty.coerceIn(1.0, 10.0)
        val r = retrievability.coerceIn(0.0, 1.0)
        val sf = w[11] *
            d.pow(-w[12]) *
            ((s + 1.0).pow(w[13]) - 1.0) *
            exp(w[14] * (1.0 - r))
        return min(sf, s).coerceIn(0.01, MAX_INTERVAL_DAYS.toDouble())
    }

    /**
     * Converts stability S into the next interval in days for target retention r:
     * I(r, S) = (S / FACTOR) * (r^(1 / DECAY) - 1).
     */
    fun nextIntervalDays(
        stability: Double,
        desiredRetention: Double = DESIRED_RETENTION,
        maxIntervalDays: Int = MAX_INTERVAL_DAYS
    ): Int {
        val r = desiredRetention.coerceIn(0.70, 0.99)
        val rawInterval = (stability / FACTOR) * (r.pow(1.0 / DECAY) - 1.0)
        return rawInterval.roundToInt().coerceIn(1, maxIntervalDays)
    }

    /**
     * Schedules the next review for [note] given [grade] using FSRS-5.
     */
    fun schedule(
        note: RevisionNoteEntity,
        grade: ReviewGrade,
        nowMs: Long = System.currentTimeMillis(),
        desiredRetention: Double = DESIRED_RETENTION
    ): RevisionNoteEntity {
        // Ensure any unmigrated card that has historical repetitions/intervals but 0 stability is normalized first
        val normalizedNote = if (note.stability <= 0f && (note.repetitions > 0 || note.intervalDays > 0)) {
            val migrated = migrateSm2ToFsrs(
                easeFactor = 2.5f,
                intervalDays = note.intervalDays,
                repetitions = note.repetitions,
                lastReviewedAt = note.lastReviewedAt,
                nowMs = nowMs
            )
            note.copy(
                stability = migrated.stability,
                difficulty = migrated.difficulty,
                retrievability = migrated.retrievability,
                state = migrated.state
            )
        } else {
            note
        }

        val currentState = CardState.fromString(normalizedNote.state)
        val priorS = normalizedNote.stability.toDouble()
        val priorD = normalizedNote.difficulty.toDouble()
        val elapsedDays = when {
            normalizedNote.lastReviewedAt != null ->
                max(0.0, (nowMs - normalizedNote.lastReviewedAt) / MILLIS_PER_DAY)
            normalizedNote.intervalDays > 0 ->
                normalizedNote.intervalDays.toDouble()
            else -> 0.0
        }

        val nextStability: Double
        val nextDifficulty: Double
        val currentRetrievability: Double
        val nextState: CardState
        val nextIntervalDays: Int
        val nextDueMs: Long
        val nextReps: Int

        when (currentState) {
            CardState.NEW -> {
                currentRetrievability = 1.0
                nextStability = initialStability(grade)
                nextDifficulty = initialDifficulty(grade)
                when (grade) {
                    ReviewGrade.AGAIN -> {
                        nextState = CardState.LEARNING
                        nextIntervalDays = 0
                        nextDueMs = nowMs + TimeUnit.MINUTES.toMillis(LEARNING_AGAIN_MINUTES)
                        nextReps = 0
                    }
                    ReviewGrade.HARD -> {
                        nextState = CardState.LEARNING
                        nextIntervalDays = 0
                        nextDueMs = nowMs + TimeUnit.MINUTES.toMillis(LEARNING_HARD_MINUTES)
                        nextReps = 1
                    }
                    ReviewGrade.GOOD -> {
                        nextState = CardState.REVIEW
                        nextIntervalDays = nextIntervalDays(nextStability, desiredRetention)
                        nextDueMs = nowMs + TimeUnit.DAYS.toMillis(nextIntervalDays.toLong())
                        nextReps = 1
                    }
                    ReviewGrade.EASY -> {
                        nextState = CardState.REVIEW
                        val goodInterval = nextIntervalDays(initialStability(ReviewGrade.GOOD), desiredRetention)
                        nextIntervalDays = max(
                            nextIntervalDays(nextStability, desiredRetention),
                            goodInterval + 1
                        ).coerceAtMost(MAX_INTERVAL_DAYS)
                        nextDueMs = nowMs + TimeUnit.DAYS.toMillis(nextIntervalDays.toLong())
                        nextReps = 1
                    }
                }
            }

            CardState.LEARNING, CardState.RELEARNING -> {
                val baseS = if (priorS > 0.0) priorS else initialStability(grade)
                val baseD = if (priorD in 1.0..10.0) priorD else initialDifficulty(grade)
                currentRetrievability = computeRetrievability(elapsedDays, baseS)
                nextStability = nextShortTermStability(baseS, grade)
                nextDifficulty = nextDifficulty(baseD, grade)

                when (grade) {
                    ReviewGrade.AGAIN -> {
                        nextState = currentState
                        nextIntervalDays = 0
                        val stepMinutes = if (currentState == CardState.RELEARNING) {
                            RELEARNING_AGAIN_MINUTES
                        } else {
                            LEARNING_AGAIN_MINUTES
                        }
                        nextDueMs = nowMs + TimeUnit.MINUTES.toMillis(stepMinutes)
                        nextReps = if (currentState == CardState.LEARNING) 0 else normalizedNote.repetitions
                    }
                    ReviewGrade.HARD -> {
                        nextState = currentState
                        nextIntervalDays = 0
                        val stepMinutes = if (currentState == CardState.RELEARNING) {
                            RELEARNING_HARD_MINUTES
                        } else {
                            LEARNING_STEP2_HARD_MINUTES
                        }
                        nextDueMs = nowMs + TimeUnit.MINUTES.toMillis(stepMinutes)
                        nextReps = normalizedNote.repetitions + 1
                    }
                    ReviewGrade.GOOD -> {
                        nextState = CardState.REVIEW
                        nextIntervalDays = nextIntervalDays(nextStability, desiredRetention)
                        nextDueMs = nowMs + TimeUnit.DAYS.toMillis(nextIntervalDays.toLong())
                        nextReps = normalizedNote.repetitions + 1
                    }
                    ReviewGrade.EASY -> {
                        nextState = CardState.REVIEW
                        val goodS = nextShortTermStability(baseS, ReviewGrade.GOOD)
                        val goodInterval = nextIntervalDays(goodS, desiredRetention)
                        nextIntervalDays = max(
                            nextIntervalDays(nextStability, desiredRetention),
                            goodInterval + 1
                        ).coerceAtMost(MAX_INTERVAL_DAYS)
                        nextDueMs = nowMs + TimeUnit.DAYS.toMillis(nextIntervalDays.toLong())
                        nextReps = normalizedNote.repetitions + 1
                    }
                }
            }

            CardState.REVIEW -> {
                val baseS = if (priorS > 0.0) priorS else max(1.0, normalizedNote.intervalDays.toDouble())
                val baseD = if (priorD in 1.0..10.0) priorD else 5.0
                val effectiveElapsed = if (elapsedDays > 0.0) elapsedDays else max(1.0, normalizedNote.intervalDays.toDouble())
                currentRetrievability = computeRetrievability(effectiveElapsed, baseS)
                nextDifficulty = nextDifficulty(baseD, grade)

                if (grade == ReviewGrade.AGAIN) {
                    nextStability = nextForgetStability(baseD, baseS, currentRetrievability)
                    nextState = CardState.RELEARNING
                    nextIntervalDays = 0
                    nextDueMs = nowMs + TimeUnit.MINUTES.toMillis(RELEARNING_AGAIN_MINUTES)
                    nextReps = normalizedNote.repetitions + 1
                } else {
                    val sHard = nextRecallStability(baseD, baseS, currentRetrievability, ReviewGrade.HARD)
                    val sGood = nextRecallStability(baseD, baseS, currentRetrievability, ReviewGrade.GOOD)
                    val sEasy = nextRecallStability(baseD, baseS, currentRetrievability, ReviewGrade.EASY)

                    val iHard = nextIntervalDays(sHard, desiredRetention).coerceAtLeast(1)
                    val iGood = max(nextIntervalDays(sGood, desiredRetention), iHard + 1).coerceAtMost(MAX_INTERVAL_DAYS)
                    val iEasy = max(nextIntervalDays(sEasy, desiredRetention), iGood + 1).coerceAtMost(MAX_INTERVAL_DAYS)

                    nextState = CardState.REVIEW
                    nextReps = normalizedNote.repetitions + 1
                    when (grade) {
                        ReviewGrade.HARD -> {
                            nextStability = sHard
                            nextIntervalDays = iHard
                        }
                        ReviewGrade.GOOD -> {
                            nextStability = sGood
                            nextIntervalDays = iGood
                        }
                        ReviewGrade.EASY -> {
                            nextStability = sEasy
                            nextIntervalDays = iEasy
                        }
                        ReviewGrade.AGAIN -> error("Handled above")
                    }
                    nextDueMs = nowMs + TimeUnit.DAYS.toMillis(nextIntervalDays.toLong())
                }
            }
        }

        // Retrievability stored on the card reflects current recall probability at scheduling time
        val storedRetrievability = if (nextIntervalDays == 0) {
            currentRetrievability.toFloat()
        } else {
            // At the start of the newly scheduled interval, retrievability is 1.0 (or currentRetrievability)
            currentRetrievability.toFloat().coerceIn(0f, 1f)
        }

        return normalizedNote.copy(
            stability = nextStability.toFloat(),
            difficulty = nextDifficulty.toFloat(),
            retrievability = storedRetrievability,
            state = nextState.name,
            intervalDays = nextIntervalDays,
            repetitions = nextReps,
            dueDate = nextDueMs,
            lastReviewedAt = nowMs,
            updatedAt = nowMs,
            syncStatus = SyncStatus.PENDING_UPLOAD
        )
    }

    /**
     * Safely converts existing SM-2 parameters (easeFactor, intervalDays, repetitions, lastReviewedAt)
     * into FSRS-5 parameters (stability, difficulty, retrievability, state) so no study progress is lost.
     */
    fun migrateSm2ToFsrs(
        easeFactor: Float,
        intervalDays: Int,
        repetitions: Int,
        lastReviewedAt: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ): FsrsMemoryState {
        // Unreviewed new card
        if (repetitions <= 0 && intervalDays <= 0 && lastReviewedAt == null) {
            return FsrsMemoryState(
                stability = 0f,
                difficulty = 0f,
                retrievability = 1f,
                state = CardState.NEW.name,
                intervalDays = 0,
                repetitions = 0
            )
        }

        // Inverse linear mapping from SM-2 Ease Factor [1.3, 3.0] -> FSRS Difficulty [1.0, 10.0]
        // EF = 2.5 -> D ≈ 5.06, EF = 1.3 -> D = 10.0, EF >= 3.0 -> D = 3.0
        val clampedEf = easeFactor.coerceIn(1.3f, 3.3f)
        val mappedDifficulty = (10.0f - ((clampedEf - 1.3f) / 1.7f) * 7.0f).coerceIn(1.0f, 10.0f)

        val state: CardState
        val stability: Float
        if (intervalDays > 0) {
            state = CardState.REVIEW
            // Under 90% desired retention, FSRS stability S equals the interval in days at R = 0.90
            stability = max(intervalDays.toFloat(), DEFAULT_WEIGHTS[0].toFloat())
        } else {
            // Card was reviewed at least once but is in a short/reset step
            state = if (repetitions > 0) CardState.LEARNING else CardState.RELEARNING
            stability = DEFAULT_WEIGHTS[0].toFloat()
        }

        val retrievability = if (lastReviewedAt != null && lastReviewedAt > 0L) {
            val elapsedDays = max(0.0, (nowMs - lastReviewedAt) / MILLIS_PER_DAY)
            computeRetrievability(elapsedDays, stability.toDouble()).toFloat()
        } else {
            0.9f
        }

        return FsrsMemoryState(
            stability = stability,
            difficulty = mappedDifficulty,
            retrievability = retrievability.coerceIn(0f, 1f),
            state = state.name,
            intervalDays = intervalDays.coerceAtLeast(0),
            repetitions = repetitions.coerceAtLeast(0)
        )
    }
}

/**
 * Forwarding facade kept for source compatibility with callers importing Sm2Scheduler.
 * All scheduling is delegated to [FsrsScheduler].
 */
object Sm2Scheduler {
    fun schedule(note: RevisionNoteEntity, grade: ReviewGrade): RevisionNoteEntity =
        FsrsScheduler.schedule(note, grade)
}

