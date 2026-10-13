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
    val history: List<DayStat> = emptyList()   // last 30 days, oldest first
) {
    val isEmpty: Boolean get() = !isLoading && totals.completedSessions == 0 && totals.skippedSessions == 0
}

class AnalyticsViewModel : ViewModel() {

    private val repository = FocusFlowApplication.instance.sessionRepository

    val state: StateFlow<AnalyticsState> = repository.getAllSessions()
        .map(::compute)
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
        return AnalyticsState(
            isLoading = false,
            totals = StatsCalculator.totals(sessions, today),
            averageSessionMinutes = if (completed.isEmpty()) 0 else completed.sumOf { it.durationSeconds } / 60 / completed.size,
            week = (6L downTo 0L).map { day(today.minusDays(it)) },
            history = (HISTORY_DAYS - 1L downTo 0L).map { day(today.minusDays(it)) }
        )
    }

    private companion object {
        const val HISTORY_DAYS = 30L
    }
}
