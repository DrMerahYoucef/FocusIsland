package com.example.data.challenge

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ChallengeResult { DONE, FAILED }

/** A goal with a deadline. [result] and [finishedAt] are set once it is reached or the time is up. */
data class Challenge(
    val id: Long,
    val goal: String,
    val startAt: Long,
    val endAt: Long,
    val finishedAt: Long? = null,
    val result: ChallengeResult? = null
) {
    val durationMs: Long get() = endAt - startAt
    val actualDurationMs: Long get() = ((finishedAt ?: endAt) - startAt).coerceAtLeast(durationMs)
}

data class ChallengeState(val active: Challenge? = null, val history: List<Challenge> = emptyList()) {
    val doneCount: Int get() = history.count { it.result == ChallengeResult.DONE }
}

/**
 * Challenges live in their own small preferences file as JSON (no database change):
 * at most one active challenge and the history, newest first.
 */
class ChallengeStore(context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ChallengeState> = _state.asStateFlow()

    @Synchronized
    fun start(goal: String, endAt: Long): Challenge {
        val now = clock()
        val historyWithPrevious = _state.value.active?.let { prev ->
            if (prev.result == ChallengeResult.FAILED || now >= prev.endAt) {
                val finalized = prev.copy(finishedAt = now, result = ChallengeResult.FAILED)
                upsertHistory(finalized, _state.value.history)
            } else {
                _state.value.history
            }
        } ?: _state.value.history

        val challenge = Challenge(id = now, goal = goal.trim(), startAt = now, endAt = endAt)
        save(ChallengeState(active = challenge, history = historyWithPrevious))
        return challenge
    }

    /** Ends the active challenge with [result]. Returns it, or null if none was active. */
    @Synchronized
    fun finish(result: ChallengeResult): Challenge? {
        val active = _state.value.active ?: return null
        val done = active.copy(finishedAt = clock(), result = result)
        save(ChallengeState(active = null, history = upsertHistory(done, _state.value.history)))
        return done
    }

    /**
     * Marks the active challenge failed when its countdown reaches 00:00, recording it in history
     * as FAILED while keeping it in [ChallengeState.active] so the overtime clock keeps counting up.
     */
    @Synchronized
    fun expireIfDue(): Challenge? {
        val active = _state.value.active ?: return null
        val now = clock()
        if (now < active.endAt) return null
        if (active.result == ChallengeResult.FAILED) return active
        val failed = active.copy(finishedAt = now, result = ChallengeResult.FAILED)
        save(ChallengeState(active = failed, history = upsertHistory(failed, _state.value.history)))
        return failed
    }

    /** Stops the overtime timer on an expired challenge and records total time spent in history. */
    @Synchronized
    fun stopOvertime(): Challenge? {
        val active = _state.value.active ?: return null
        val now = clock()
        val finalized = active.copy(finishedAt = maxOf(now, active.endAt), result = ChallengeResult.FAILED)
        save(ChallengeState(active = null, history = upsertHistory(finalized, _state.value.history)))
        return finalized
    }

    fun clearHistory() = save(_state.value.copy(history = emptyList()))

    private fun upsertHistory(entry: Challenge, current: List<Challenge>): List<Challenge> {
        val filtered = current.filterNot { it.id == entry.id }
        return (listOf(entry) + filtered).take(MAX_HISTORY)
    }

    private fun save(state: ChallengeState) {
        _state.value = state
        prefs.edit()
            .putString(KEY_ACTIVE, state.active?.toJson()?.toString())
            .putString(KEY_HISTORY, JSONArray(state.history.map { it.toJson() }).toString())
            .apply()
    }

    private fun load(): ChallengeState = runCatching {
        val active = prefs.getString(KEY_ACTIVE, null)?.let { challengeFromJson(JSONObject(it)) }
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        ChallengeState(active, (0 until array.length()).map { challengeFromJson(array.getJSONObject(it)) })
    }.getOrDefault(ChallengeState())

    companion object {
        private const val PREFS = "challenges"
        private const val KEY_ACTIVE = "active"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY = 200

        internal fun Challenge.toJson(): JSONObject = JSONObject()
            .put("id", id).put("goal", goal).put("startAt", startAt).put("endAt", endAt)
            .apply {
                finishedAt?.let { put("finishedAt", it) }
                result?.let { put("result", it.name) }
            }

        internal fun challengeFromJson(o: JSONObject) = Challenge(
            id = o.getLong("id"),
            goal = o.optString("goal"),
            startAt = o.getLong("startAt"),
            endAt = o.getLong("endAt"),
            finishedAt = if (o.has("finishedAt")) o.getLong("finishedAt") else null,
            result = o.optString("result").takeIf { it.isNotEmpty() }?.let { runCatching { ChallengeResult.valueOf(it) }.getOrNull() }
        )
    }
}
