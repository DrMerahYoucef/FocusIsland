package com.example

import com.example.ui.screen.timer.dayBlocks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DayBlocksTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 13)
    private fun at(h: Int, m: Int) = day.atTime(h, m).toInstant(zone).toEpochMilli()

    @Test
    fun sessionsBecomeMinutesSinceMidnight() {
        val blocks = dayBlocks(listOf(Triple(at(9, 25), 25 * 60, true), Triple(at(18, 10), 10 * 60, false)), day, zone)
        assertEquals(2, blocks.size)
        assertEquals(9 * 60f, blocks[0].startMinute, 0.01f)
        assertEquals(9 * 60f + 25, blocks[0].endMinute, 0.01f)
        assertEquals(false, blocks[1].completed)
    }

    @Test
    fun sessionAcrossMidnightIsCutAndOtherDaysIgnored() {
        val crossing = day.atStartOfDay(zone).toInstant().toEpochMilli() + 10 * 60_000L // ended 00:10, lasted 25 min
        val yesterday = at(9, 0) - 86_400_000L
        val blocks = dayBlocks(listOf(Triple(crossing, 25 * 60, true), Triple(yesterday, 1500, true)), day, zone)
        assertEquals(1, blocks.size)
        assertEquals(0f, blocks[0].startMinute, 0.01f)
        assertEquals(10f, blocks[0].endMinute, 0.01f)
    }
}
