package com.example

import com.example.data.db.entity.SessionEntity
import com.example.ui.screen.timer.timeOfDayMinutes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FocusModeTest {
    private fun session(hour: Int, minutes: Int) = SessionEntity(
        date = LocalDate.of(2026, 5, 4).atTime(hour, 30).toInstant(ZoneOffset.UTC).toEpochMilli(),
        durationSeconds = minutes * 60,
        completed = true,
        focusScore = minutes
    )

    @Test
    fun bucketsMinutesByTimeOfDay() {
        val result = timeOfDayMinutes(
            listOf(session(7, 25), session(11, 25), session(14, 50), session(20, 10), session(2, 5)),
            ZoneOffset.UTC
        ).map { it.second }
        assertEquals(listOf(50, 50, 10, 5), result) // morning, afternoon, evening, night
    }
}
