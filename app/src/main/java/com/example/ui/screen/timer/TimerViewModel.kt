package com.example.ui.screen.timer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.service.PomodoroTimerService
import com.example.service.TimerState
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.data.stats.StatsCalculator
import kotlinx.coroutines.Dispatchers
import com.example.data.LastSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class FocusToday(
    val sessions: Int = 0,
    val minutes: Int = 0,
    val streakDays: Int = 0,
    val blocks: List<DayBlock> = emptyList()
)

/** One focus session on today's 24-hour dial, in minutes since midnight. */
data class DayBlock(val startMinute: Float, val endMinute: Float, val completed: Boolean)
data class NextExam(val name: String, val daysLeft: Int)

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    val timerState: StateFlow<TimerState> = PomodoroTimerService.state

    private val app = FocusFlowApplication.instance

    /** Today's completed sessions and minutes, plus the current streak (shared StatsCalculator rules). */
    val today: StateFlow<FocusToday> = app.sessionRepository.getAllSessions()
        .map { all ->
            val completed = all.filter { it.completed }
            val todayDate = LocalDate.now()
            val todays = completed.filter { StatsCalculator.localDay(it.date) == todayDate }
            FocusToday(
                sessions = todays.size,
                minutes = todays.sumOf { it.durationSeconds } / 60,
                streakDays = StatsCalculator.currentStreak(completed.map { it.date }),
                blocks = dayBlocks(all.map { Triple(it.date, it.durationSeconds, it.completed) }, todayDate)
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusToday())

    /** The closest exam today or later, for the "next exam" chip. */
    val nextExam: StateFlow<NextExam?> = app.examRepository.getAllExams()
        .map { exams ->
            val todayDate = LocalDate.now()
            exams.map { it to StatsCalculator.localDay(it.examDate) }
                .filter { (_, day) -> !day.isBefore(todayDate) }
                .minByOrNull { (_, day) -> day }
                ?.let { (exam, day) -> NextExam(exam.name, ChronoUnit.DAYS.between(todayDate, day).toInt()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val prefs = application.getSharedPreferences("focusflow_prefs", Context.MODE_PRIVATE)

    private val _lastAmbient = MutableStateFlow(prefs.getString(LastSound.KEY_AMBIENT, null))
    /** Last ambient sound picked, offered again on the sound card. */
    val lastAmbient: StateFlow<String?> = _lastAmbient.asStateFlow()

    private val _lastSoundKind = MutableStateFlow(prefs.getString(LastSound.KEY_KIND, null))
    /** [LastSound.KIND_RADIO] or [LastSound.KIND_AMBIENT]: which one the sound card shows when nothing plays. */
    val lastSoundKind: StateFlow<String?> = _lastSoundKind.asStateFlow()

    // New durations from Settings show up immediately on an idle timer.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            LastSound.KEY_AMBIENT -> _lastAmbient.value = p.getString(key, null)
            LastSound.KEY_KIND -> _lastSoundKind.value = p.getString(key, null)
            else -> PomodoroTimerService.applyIdleSettings(getApplication())
        }
    }

    init {
        // Resumes a session that was running when the process was killed.
        PomodoroTimerService.restoreIfNeeded(application)
        PomodoroTimerService.applyIdleSettings(application)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    fun startTimer() = send(PomodoroTimerService.ACTION_START, foreground = true)
    fun pauseTimer() = send(PomodoroTimerService.ACTION_PAUSE)
    fun resumeTimer() = send(PomodoroTimerService.ACTION_RESUME, foreground = true)
    fun skipPhase() = send(PomodoroTimerService.ACTION_SKIP)
    fun stopTimer() = send(PomodoroTimerService.ACTION_STOP)

    fun setAmbientSound(ambientId: String) {
        val context = getApplication<Application>()
        if (ambientId != PomodoroTimerService.AMBIENT_NONE) LastSound.saveAmbient(prefs, ambientId)
        context.startService(
            Intent(context, PomodoroTimerService::class.java)
                .setAction(PomodoroTimerService.ACTION_SET_AMBIENT)
                .putExtra(PomodoroTimerService.EXTRA_AMBIENT_ID, ambientId)
        )
    }

    private fun send(action: String, foreground: Boolean = false) {
        val context = getApplication<Application>()
        val intent = Intent(context, PomodoroTimerService::class.java).setAction(action)
        if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }
}

/**
 * Today's sessions as dial blocks. A session is stored with its end time and length, so it
 * started `duration` earlier; a session that began before midnight is cut at midnight.
 */
internal fun dayBlocks(sessions: List<Triple<Long, Int, Boolean>>, day: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<DayBlock> {
    val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return sessions
        .filter { (end, seconds, _) -> end > midnight && end - seconds * 1000L < nextMidnight }
        .map { (end, seconds, completed) ->
            val start = (end - seconds * 1000L).coerceAtLeast(midnight)
            DayBlock((start - midnight) / 60_000f, (end.coerceAtMost(nextMidnight) - midnight) / 60_000f, completed)
        }
        .sortedBy { it.startMinute }
}
