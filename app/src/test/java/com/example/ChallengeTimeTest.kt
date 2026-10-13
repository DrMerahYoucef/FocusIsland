package com.example

import com.example.ui.screen.challenge.ChallengeTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ChallengeTimeTest {
    private val zone = ZoneOffset.UTC
    private val now = ZonedDateTime.of(2026, 10, 13, 18, 46, 30, 0, zone).toInstant().toEpochMilli()

    @Test
    fun endTimeLaterTodayStaysToday() {
        val end = ChallengeTime.endAtClock(now, LocalTime.of(19, 46), zone)
        assertEquals(ZonedDateTime.of(2026, 10, 13, 19, 46, 0, 0, zone).toInstant().toEpochMilli(), end)
    }

    @Test
    fun endTimeAlreadyPassedMeansTomorrow() {
        val end = ChallengeTime.endAtClock(now, LocalTime.of(8, 0), zone)
        assertEquals(ZonedDateTime.of(2026, 10, 14, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), end)
    }

    @Test
    fun clockFormat() {
        assertEquals("45:00", ChallengeTime.clock(45 * 60_000L))
        assertEquals("1:30:00", ChallengeTime.clock(90 * 60_000L))
        assertEquals("00:01", ChallengeTime.clock(1))
        assertEquals("00:00", ChallengeTime.clock(-5))
    }
}
