package com.example.ui.screen.challenge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.FocusFlowApplication
import com.example.data.challenge.Challenge
import com.example.data.challenge.ChallengeResult
import com.example.data.challenge.ChallengeState
import com.example.service.ChallengeAlarmReceiver
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Start, reach or give up a challenge. The deadline alarm and notification follow the store. */
class ChallengeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = FocusFlowApplication.instance.challengeStore
    val state: StateFlow<ChallengeState> = store.state

    init {
        if (store.expireIfDue() != null) ChallengeAlarmReceiver.cancel(application)
    }

    fun start(goal: String, endAt: Long) {
        val challenge = store.start(goal, endAt)
        ChallengeAlarmReceiver.schedule(getApplication(), challenge)
    }

    /** Returns the finished challenge so the screen can celebrate. */
    fun goalReached(): Challenge? = finish(ChallengeResult.DONE)

    fun giveUp() {
        finish(ChallengeResult.FAILED)
    }

    /** Called by the screen when its countdown reaches zero. */
    fun timeUp() {
        if (store.expireIfDue() != null) ChallengeAlarmReceiver.cancel(getApplication())
    }

    fun clearHistory() = store.clearHistory()

    private fun finish(result: ChallengeResult): Challenge? {
        val done = store.finish(result) ?: return null
        ChallengeAlarmReceiver.cancel(getApplication())
        return done
    }
}

/** Deadline arithmetic, kept pure so it can be unit-tested. */
object ChallengeTime {
    /** Longest challenge allowed. */
    const val MAX_MINUTES = 24 * 60

    val quickMinutes = listOf(15, 30, 45, 60, 90, 120, 180)

    fun endAfter(nowMs: Long, minutes: Int): Long = nowMs + minutes * 60_000L

    /** Next occurrence of [time] after [nowMs]: today if still ahead, otherwise tomorrow. */
    fun endAtClock(nowMs: Long, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        var end = now.with(time).withSecond(0).withNano(0)
        if (!end.isAfter(now)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }

    /** "1:05:09" from one hour up, "05:09" below. Rounded up so 00:00 only shows at the end. */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
