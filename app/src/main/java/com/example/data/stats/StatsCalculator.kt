package com.example.data.stats

import com.example.data.db.entity.SessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusTotals(
    val completedSessions: Int,
    val skippedSessions: Int,
    val focusMinutes: Int,
    val points: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int
)

/**
 * One definition of every statistic, shared by the Stats screen, widgets, the leaderboard
 * sync and the focus-mode sheet. Days are local calendar days (DST-safe via java.time).
 *
 * Points: 1 point per completed focus minute (the rule shown to users in the app).
 */
object StatsCalculator {

    fun localDay(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** Consecutive days with at least one completed session, ending today or yesterday. */
    fun currentStreak(
        completedSessionDates: Collection<Long>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val days = completedSessionDates.mapTo(HashSet()) { localDay(it, zone) }
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(
        completedSessionDates: Collection<Long>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val days = completedSessionDates.map { localDay(it, zone) }.distinct().sorted()
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in days) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }

    fun totals(
        sessions: List<SessionEntity>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): FocusTotals {
        val completed = sessions.filter { it.completed }
        val minutes = completed.sumOf { it.durationSeconds } / 60
        val dates = completed.map { it.date }
        return FocusTotals(
            completedSessions = completed.size,
            skippedSessions = sessions.size - completed.size,
            focusMinutes = minutes,
            points = minutes,
            currentStreakDays = currentStreak(dates, today, zone),
            longestStreakDays = longestStreak(dates, zone)
        )
    }
}
