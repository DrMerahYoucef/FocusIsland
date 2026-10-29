package com.example.ui.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.data.db.entity.SessionEntity
import com.example.data.stats.FocusTotals
import com.example.data.stats.StatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DayStat(
    val date: LocalDate,
    val completedSessions: Int,
    val focusMinutes: Int,
    val endedEarly: Int
)

data class AnalyticsState(
    val isLoading: Boolean = true,
    val totals: FocusTotals = FocusTotals(0, 0, 0, 0, 0, 0),
    val averageSessionMinutes: Int = 0,
    val week: List<DayStat> = emptyList(),     // last 7 days, oldest first
    val history: List<DayStat> = emptyList(),  // last 30 days, oldest first
    val weekMinutes: Int = 0,
    val monthMinutes: Int = 0,
    val year2022Minutes: Int = 0,
    val sinceJune2026Minutes: Int = 0
) {
    val isEmpty: Boolean get() = !isLoading && totals.completedSessions == 0 && totals.skippedSessions == 0 && totals.focusMinutes == 0 && totals.points == 0
}

class AnalyticsViewModel : ViewModel() {

    private val repository = FocusFlowApplication.instance.sessionRepository

    val state: StateFlow<AnalyticsState> = repository.getAllSessions()
        .map { compute(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    private fun compute(sessions: List<SessionEntity>): AnalyticsState {
        val today = LocalDate.now()
        val byDay = sessions.groupBy { StatsCalculator.localDay(it.date) }
        fun day(date: LocalDate): DayStat {
            val list = byDay[date].orEmpty()
            val done = list.filter { it.completed }
            return DayStat(date, done.size, done.sumOf { it.durationSeconds } / 60, list.size - done.size)
        }
        val completed = sessions.filter { it.completed }
        val weekList = (6L downTo 0L).map { day(today.minusDays(it)) }
        val historyList = (HISTORY_DAYS - 1L downTo 0L).map { day(today.minusDays(it)) }

        val start2022 = LocalDate.of(2022, 1, 1)
        val end2022 = LocalDate.of(2022, 12, 31)
        val startJune2026 = LocalDate.of(2026, 6, 1)

        val year2022Min = completed.filter {
            val d = StatsCalculator.localDay(it.date)
            !d.isBefore(start2022) && !d.isAfter(end2022)
        }.sumOf { it.durationSeconds } / 60

        val sinceJune2026Min = completed.filter {
            val d = StatsCalculator.localDay(it.date)
            !d.isBefore(startJune2026)
        }.sumOf { it.durationSeconds } / 60

        return AnalyticsState(
            isLoading = false,
            totals = StatsCalculator.totals(sessions, today),
            averageSessionMinutes = if (completed.isEmpty()) 0 else completed.sumOf { it.durationSeconds } / 60 / completed.size,
            week = weekList,
            history = historyList,
            weekMinutes = weekList.sumOf { it.focusMinutes },
            monthMinutes = historyList.sumOf { it.focusMinutes },
            year2022Minutes = year2022Min,
            sinceJune2026Minutes = sinceJune2026Min
        )
    }

    private companion object {
        const val HISTORY_DAYS = 30L
    }
}
