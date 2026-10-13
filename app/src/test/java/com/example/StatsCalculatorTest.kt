package com.example

import com.example.data.db.entity.SessionEntity
import com.example.data.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsCalculatorTest {

    private val zone = ZoneId.of("Europe/Paris") // has DST, catches 23h/25h day bugs

    private fun at(date: LocalDate, hour: Int) =
        date.atTime(hour, 30).atZone(zone).toInstant().toEpochMilli()

    private fun session(date: LocalDate, hour: Int = 10, minutes: Int = 25, completed: Boolean = true) =
        SessionEntity(date = at(date, hour), durationSeconds = minutes * 60, completed = completed, focusScore = minutes)

    @Test
    fun streakCountsConsecutiveLocalDaysAcrossDstChange() {
        // DST in Europe/Paris ends on 2026-10-25.
        val today = LocalDate.of(2026, 10, 26)
        val dates = (0L..3L).map { at(today.minusDays(it), 0) } // just after midnight each day
        assertEquals(4, StatsCalculator.currentStreak(dates, today, zone))
    }

    @Test
    fun streakStillAliveWhenLastSessionWasYesterday() {
        val today = LocalDate.of(2026, 3, 10)
        val dates = listOf(at(today.minusDays(1), 22), at(today.minusDays(2), 9))
        assertEquals(2, StatsCalculator.currentStreak(dates, today, zone))
    }

    @Test
    fun streakBrokenByMissedDay() {
        val today = LocalDate.of(2026, 3, 10)
        val dates = listOf(at(today.minusDays(2), 9))
        assertEquals(0, StatsCalculator.currentStreak(dates, today, zone))
    }

    @Test
    fun totalsIgnoreSkippedSessionsAndUseOnePointPerMinute() {
        val today = LocalDate.of(2026, 3, 10)
        val sessions = listOf(
            session(today, minutes = 25),
            session(today, hour = 14, minutes = 50),
            session(today.minusDays(5), minutes = 10, completed = false)
        )
        val totals = StatsCalculator.totals(sessions, today, zone)
        assertEquals(2, totals.completedSessions)
        assertEquals(1, totals.skippedSessions)
        assertEquals(75, totals.focusMinutes)
        assertEquals(75, totals.points)
        assertEquals(1, totals.currentStreakDays)
    }

    @Test
    fun longestStreakFindsBestRun() {
        val base = LocalDate.of(2026, 1, 1)
        val dates = listOf(0L, 1L, 2L, 5L, 6L).map { at(base.plusDays(it), 12) }
        assertEquals(3, StatsCalculator.longestStreak(dates, zone))
    }
}
