package com.example.service

enum class Phase(val label: String) {
    FOCUS("Focus Session"),
    SHORT_BREAK("Short Break"),
    LONG_BREAK("Long Break")
}

data class TimerState(
    val remainingMs: Long = 25 * 60 * 1000L,
    val phase: Phase = Phase.FOCUS,
    val sessionCount: Int = 0,
    val isRunning: Boolean = false,
    val totalFocusSecs: Long = 0L,
    val focusDurationMs: Long = 25 * 60 * 1000L,
    val shortBreakDurationMs: Long = 5 * 60 * 1000L,
    val longBreakDurationMs: Long = 15 * 60 * 1000L,
    val sessionsBeforeLongBreak: Int = 4,
    val isDndActive: Boolean = false,
    val currentAmbientId: String = "none",
    val isAutoPausedByAppSwitch: Boolean = false,
    val lastAutoResumedAt: Long = 0L
) {

    fun durationOf(p: Phase): Long = when (p) {
        Phase.FOCUS -> focusDurationMs
        Phase.SHORT_BREAK -> shortBreakDurationMs
        Phase.LONG_BREAK -> longBreakDurationMs
    }

    /** Length of the current phase, fixed when the phase began. */
    val phaseDurationMs: Long get() = durationOf(phase)

    /** True when the current phase hasn't been started or paused yet. */
    val isUntouched: Boolean get() = !isRunning && remainingMs == phaseDurationMs
}
