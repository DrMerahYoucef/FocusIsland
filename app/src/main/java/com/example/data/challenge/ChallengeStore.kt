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

    fun start(goal: String, endAt: Long): Challenge {
        val now = clock()
        val challenge = Challenge(id = now, goal = goal.trim(), startAt = now, endAt = endAt)
        save(_state.value.copy(active = challenge))
        return challenge
    }

    /** Ends the active challenge with [result]. Returns it, or null if none was active. */
    @Synchronized
    fun finish(result: ChallengeResult): Challenge? {
        val active = _state.value.active ?: return null
        val done = active.copy(finishedAt = clock(), result = result)
        save(ChallengeState(active = null, history = (listOf(done) + _state.value.history).take(MAX_HISTORY)))
        return done
    }

    /** Marks the active challenge failed if its time is up (missed alarm, reboot). */
    fun expireIfDue(): Challenge? =
        _state.value.active?.takeIf { clock() >= it.endAt }?.let { finish(ChallengeResult.FAILED) }

    fun clearHistory() = save(_state.value.copy(history = emptyList()))

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
