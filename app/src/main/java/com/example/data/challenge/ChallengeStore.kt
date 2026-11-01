package com.example.data.challenge

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ChallengeResult { DONE, FAILED }

enum class PersonalChallengeType { GOAL, LOCK_PHONE }

data class MotivationalQuote(val quote: String, val author: String)

object ChallengeQuotes {
    val quotes: List<MotivationalQuote> = listOf(
        MotivationalQuote("Deep work is the superpower of the 21st century.", "Cal Newport"),
        MotivationalQuote("Starve your distractions, feed your focus.", "Daniel Goleman"),
        MotivationalQuote("The successful warrior is the average person, with laser-like focus.", "Bruce Lee"),
        MotivationalQuote("Discipline is choosing between what you want now and what you want most.", "Abraham Lincoln"),
        MotivationalQuote("You will never reach your destination if you stop and throw stones at every dog that barks.", "Winston Churchill"),
        MotivationalQuote("Concentrate all your thoughts upon the work at hand. The sun's rays do not burn until brought to a focus.", "Alexander Graham Bell"),
        MotivationalQuote("What you stay focused on will grow.", "Roy T. Bennett"),
        MotivationalQuote("Do not let what you cannot do interfere with what you can do.", "John Wooden"),
        MotivationalQuote("Focus is a matter of deciding what things you're not going to do.", "John Carmack"),
        MotivationalQuote("One hour of unbroken focus beats an entire day of scattered effort.", "Focus Island"),
        MotivationalQuote("Your future self is watching you right now through your memories. Make them proud.", "Focus Island"),
        MotivationalQuote("Silence the noise. Master this moment.", "Focus Island")
    )

    fun pickRandom(): MotivationalQuote = quotes.random()
}

/** A goal or strict phone lock with a deadline. [result] and [finishedAt] are set once it is reached or the time is up. */
data class Challenge(
    val id: Long,
    val goal: String,
    val startAt: Long,
    val endAt: Long,
    val finishedAt: Long? = null,
    val result: ChallengeResult? = null,
    val type: PersonalChallengeType = PersonalChallengeType.GOAL,
    val quoteText: String = "",
    val quoteAuthor: String = ""
) {
    val durationMs: Long get() = endAt - startAt
    val actualDurationMs: Long get() = ((finishedAt ?: endAt) - startAt).coerceAtLeast(durationMs)
    val isLockPhone: Boolean get() = type == PersonalChallengeType.LOCK_PHONE
}

data class ChallengeState(val active: Challenge? = null, val history: List<Challenge> = emptyList()) {
    val doneCount: Int get() = history.count { it.result == ChallengeResult.DONE }
}

/**
 * Challenges live in their own small preferences file as JSON and sync to Firebase Firestore
 * on `/users/{uid}` (`personalActiveChallengeJson`, `personalChallengeHistoryJson`) when signed in:
 * at most one active challenge and the history, newest first.
 */
class ChallengeStore(context: Context, private val clock: () -> Long = System::currentTimeMillis) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ChallengeState> = _state.asStateFlow()

    init {
        observeFirestoreSync()
    }

    private fun observeFirestoreSync() {
        runCatching {
            Firebase.auth.addAuthStateListener(FirebaseAuth.AuthStateListener { auth ->
                val uid = auth.currentUser?.takeIf { !it.isAnonymous }?.uid.orEmpty()
                if (uid.isNotEmpty()) {
                    pullFromFirestoreAndSync(uid)
                }
            })
            val initialUid = Firebase.auth.currentUser?.takeIf { !it.isAnonymous }?.uid.orEmpty()
            if (initialUid.isNotEmpty()) {
                pullFromFirestoreAndSync(initialUid)
            }
        }
    }

    private fun pullFromFirestoreAndSync(uid: String) {
        runCatching {
            Firebase.firestore.collection("users").document(uid).get()
                .addOnSuccessListener { snap ->
                    if (snap == null || !snap.exists()) {
                        pushToFirestore(uid, _state.value)
                        return@addOnSuccessListener
                    }
                    val remoteHistoryRaw = snap.getString("personalChallengeHistoryJson")
                    val remoteHistory = if (!remoteHistoryRaw.isNullOrBlank()) {
                        runCatching {
                            val arr = JSONArray(remoteHistoryRaw)
                            (0 until arr.length()).mapNotNull { i ->
                                runCatching { challengeFromJson(arr.getJSONObject(i)) }.getOrNull()
                            }
                        }.getOrDefault(emptyList())
                    } else emptyList()

                    val mergedHistory = (_state.value.history + remoteHistory)
                        .distinctBy { it.id }
                        .sortedByDescending { it.finishedAt ?: it.startAt }
                        .take(MAX_HISTORY)

                    if (mergedHistory != _state.value.history) {
                        val nextState = _state.value.copy(history = mergedHistory)
                        _state.value = nextState
                        prefs.edit()
                            .putString(KEY_HISTORY, JSONArray(mergedHistory.map { it.toJson() }).toString())
                            .apply()
                    }
                    pushToFirestore(uid, _state.value)
                }
        }
    }

    private fun pushToFirestore(uid: String, state: ChallengeState) {
        if (uid.isEmpty()) return
        runCatching {
            val activeJson = state.active?.toJson()?.toString().orEmpty()
            val historyJson = JSONArray(state.history.take(MAX_HISTORY).map { it.toJson() }).toString()
            Firebase.firestore.collection("users").document(uid)
                .set(
                    mapOf(
                        "personalActiveChallengeJson" to activeJson,
                        "personalChallengeHistoryJson" to historyJson,
                        "personalChallengesDoneCount" to state.doneCount
                    ),
                    SetOptions.merge()
                )
        }
    }

    @Synchronized
    fun start(
        goal: String,
        endAt: Long,
        type: PersonalChallengeType = PersonalChallengeType.GOAL,
        quote: MotivationalQuote? = null
    ): Challenge {
        val now = clock()
        val historyWithPrevious = _state.value.active?.let { prev ->
            if (prev.isLockPhone && now >= prev.endAt) {
                val finalized = prev.copy(finishedAt = prev.endAt, result = ChallengeResult.DONE)
                upsertHistory(finalized, _state.value.history)
            } else if (prev.result == ChallengeResult.FAILED || now >= prev.endAt) {
                val finalized = prev.copy(finishedAt = now, result = ChallengeResult.FAILED)
                upsertHistory(finalized, _state.value.history)
            } else {
                _state.value.history
            }
        } ?: _state.value.history

        val chosenQuote = if (type == PersonalChallengeType.LOCK_PHONE) {
            quote ?: ChallengeQuotes.pickRandom()
        } else {
            quote
        }
        val cleanGoal = goal.trim().ifBlank {
            if (type == PersonalChallengeType.LOCK_PHONE) "Lock Phone Challenge" else ""
        }
        val challenge = Challenge(
            id = now,
            goal = cleanGoal,
            startAt = now,
            endAt = endAt,
            type = type,
            quoteText = chosenQuote?.quote.orEmpty(),
            quoteAuthor = chosenQuote?.author.orEmpty()
        )
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
     * For [PersonalChallengeType.GOAL]: marks the active challenge failed when its countdown reaches 00:00,
     * recording it in history as FAILED while keeping it in [ChallengeState.active] so the overtime clock keeps counting up.
     *
     * For [PersonalChallengeType.LOCK_PHONE]: reaching 00:00 completes the lock session successfully as DONE
     * and unlocks the phone (`active = null`).
     */
    @Synchronized
    fun expireIfDue(): Challenge? {
        val active = _state.value.active ?: return null
        val now = clock()
        if (now < active.endAt) return null
        if (active.isLockPhone) {
            val done = active.copy(finishedAt = active.endAt, result = ChallengeResult.DONE)
            save(ChallengeState(active = null, history = upsertHistory(done, _state.value.history)))
            return done
        }
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
        val uid = runCatching { Firebase.auth.currentUser?.takeIf { !it.isAnonymous }?.uid.orEmpty() }.getOrDefault("")
        if (uid.isNotEmpty()) {
            pushToFirestore(uid, state)
        }
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
            .put("id", id)
            .put("goal", goal)
            .put("startAt", startAt)
            .put("endAt", endAt)
            .put("type", type.name)
            .put("quoteText", quoteText)
            .put("quoteAuthor", quoteAuthor)
            .apply {
                finishedAt?.let { put("finishedAt", it) }
                result?.let { put("result", it.name) }
            }

        internal fun challengeFromJson(o: JSONObject): Challenge {
            val parsedType = o.optString("type").takeIf { it.isNotEmpty() }?.let {
                runCatching { PersonalChallengeType.valueOf(it) }.getOrNull()
            } ?: PersonalChallengeType.GOAL
            return Challenge(
                id = o.getLong("id"),
                goal = o.optString("goal"),
                startAt = o.getLong("startAt"),
                endAt = o.getLong("endAt"),
                finishedAt = if (o.has("finishedAt")) o.getLong("finishedAt") else null,
                result = o.optString("result").takeIf { it.isNotEmpty() }?.let { runCatching { ChallengeResult.valueOf(it) }.getOrNull() },
                type = parsedType,
                quoteText = o.optString("quoteText", ""),
                quoteAuthor = o.optString("quoteAuthor", "")
            )
        }
    }
}

