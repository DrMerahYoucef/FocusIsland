package com.example

import com.example.widget.WidgetText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetTextTest {
    private fun at(date: LocalDate, hour: Int) =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun countsCalendarDaysNotHours() {
        val today = LocalDate.of(2026, 3, 27)
        // An exam tomorrow morning is 1 day away even if less than 24 h remain.
        assertEquals(1, WidgetText.daysUntil(at(today.plusDays(1), 8), today))
        assertEquals(0, WidgetText.daysUntil(at(today, 23), today))
        // Across a DST change (late March in Europe) the count stays exact.
        assertEquals(10, WidgetText.daysUntil(at(today.plusDays(10), 9), today))
    }

    @Test
    fun pastExamsCountAsToday() {
        val today = LocalDate.of(2026, 1, 10)
        assertEquals(0, WidgetText.daysUntil(at(today.minusDays(3), 9), today))
    }
}
