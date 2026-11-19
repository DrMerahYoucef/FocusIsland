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
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

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
    val quoteAuthor: String = "",
    val allowedPackages: Set<String> = emptySet()
) {
    val durationMs: Long get() = endAt - startAt
    val actualDurationMs: Long get() = ((finishedAt ?: endAt) - startAt).coerceAtLeast(durationMs)
    val isLockPhone: Boolean get() = type == PersonalChallengeType.LOCK_PHONE
}

/**
 * A recurring daily Lock Phone interval (e.g. 22:00 – 07:00 or 14:00 – 16:00).
 * Every day, a 5-minute pre-lock warning appears in the Dynamic Island before [startHour]:[startMinute],
 * with an unlimited "+1 min" snooze button whenever less than 30 seconds remain ([snoozeUntilMs]).
 */
data class DailyLockSchedule(
    val id: String,
    val label: String = "",
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
    val allowedPackages: Set<String> = emptySet(),
    val snoozeUntilMs: Long = 0L
) {
    val startTime: LocalTime get() = LocalTime.of(startHour.coerceIn(0, 23), startMinute.coerceIn(0, 59))
    val endTime: LocalTime get() = LocalTime.of(endHour.coerceIn(0, 23), endMinute.coerceIn(0, 59))
    val isValid: Boolean get() = startHour != endHour || startMinute != endMinute
    val durationMinutes: Int get() = DailyLockTime.durationMinutes(startHour, startMinute, endHour, endMinute)
    val isOvernight: Boolean get() = DailyLockTime.isOvernight(startHour, startMinute, endHour, endMinute)
}

/** Resolved timestamps for the current or next occurrence of a [DailyLockSchedule]. */
data class DailyLockOccurrence(
    val schedule: DailyLockSchedule,
    val baseStartMs: Long,
    val effectiveStartMs: Long,
    val warningAtMs: Long,
    val endAtMs: Long
) {
    fun isActiveAt(nowMs: Long): Boolean = nowMs >= effectiveStartMs && nowMs < endAtMs
    fun isWarningAt(nowMs: Long): Boolean = nowMs >= warningAtMs && nowMs < effectiveStartMs
    fun remainingBeforeLockMs(nowMs: Long): Long = (effectiveStartMs - nowMs).coerceAtLeast(0L)
}

/** Pure daily lock interval arithmetic for reliable alarms and unit testing. */
object DailyLockTime {
    const val WARNING_LEAD_MS = 5 * 60_000L
    const val SNOOZE_THRESHOLD_MS = 30_000L
    const val SNOOZE_STEP_MS = 60_000L

    fun isOvernight(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): Boolean {
        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute
        return endTotal < startTotal
    }

    fun durationMinutes(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): Int {
        val startTotal = startHour * 60 + startMinute
        val endTotal = endHour * 60 + endMinute
        return when {
            endTotal > startTotal -> endTotal - startTotal
            endTotal < startTotal -> (24 * 60 - startTotal) + endTotal
            else -> 0
        }
    }

    /**
     * Resolves the active or next upcoming occurrence of [schedule] relative to [nowMs],
     * accounting for overnight windows (e.g. 22:00 – 07:00) and any active `+1 min` [DailyLockSchedule.snoozeUntilMs].
     */
    fun nextOccurrence(
        schedule: DailyLockSchedule,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): DailyLockOccurrence? {
        if (!schedule.enabled || !schedule.isValid) return null
        val nowZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val today = nowZdt.toLocalDate()
        val durationMin = schedule.durationMinutes.toLong()

        // Candidate dates: yesterday (catches active overnight window), today, tomorrow
        val candidates = listOf(today.minusDays(1), today, today.plusDays(1))
        for (date in candidates) {
            val startZdt = date.atTime(schedule.startTime).atZone(zone)
            val endZdt = startZdt.plusMinutes(durationMin)
            val baseStartMs = startZdt.toInstant().toEpochMilli()
            val endAtMs = endZdt.toInstant().toEpochMilli()
            if (nowMs >= endAtMs) continue

            // Apply snooze if it falls within this occurrence's window
            val snoozeApplies = schedule.snoozeUntilMs > baseStartMs && schedule.snoozeUntilMs <= endAtMs
            val effectiveStartMs = if (snoozeApplies) schedule.snoozeUntilMs else baseStartMs
            // If user snoozed all the way past the end of this window, skip to the next day's window
            if (effectiveStartMs >= endAtMs) continue

            val warningAtMs = effectiveStartMs - WARNING_LEAD_MS
            return DailyLockOccurrence(
                schedule = schedule,
                baseStartMs = baseStartMs,
                effectiveStartMs = effectiveStartMs,
                warningAtMs = warningAtMs,
                endAtMs = endAtMs
            )
        }
        return null
    }

    /** Picks the most urgent occurrence that is either currently active or inside its 5-minute warning window. */
    fun findImminentOrActive(
        schedules: List<DailyLockSchedule>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): DailyLockOccurrence? {
        val resolved = schedules.mapNotNull { nextOccurrence(it, nowMs, zone) }
        val active = resolved.filter { it.isActiveAt(nowMs) }.minByOrNull { it.endAtMs }
        if (active != null) return active
        return resolved.filter { it.isWarningAt(nowMs) }.minByOrNull { it.effectiveStartMs }
    }
}

data class ChallengeState(
    val active: Challenge? = null,
    val history: List<Challenge> = emptyList(),
    val dailySchedules: List<DailyLockSchedule> = emptyList()
) {
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

    private val _defaultAllowedPackages = MutableStateFlow(loadDefaultAllowedPackages())
    val defaultAllowedPackages: StateFlow<Set<String>> = _defaultAllowedPackages.asStateFlow()

    init {
        observeFirestoreSync()
    }

    fun getDefaultAllowedPackages(): Set<String> = _defaultAllowedPackages.value

    @Synchronized
    fun setDefaultAllowedPackages(packages: Set<String>) {
        val cleaned = packages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        _defaultAllowedPackages.value = cleaned
        prefs.edit()
            .putString(KEY_DEFAULT_ALLOWED_PACKAGES, JSONArray(cleaned.toList()).toString())
            .apply()
    }

    private fun loadDefaultAllowedPackages(): Set<String> = runCatching {
        val raw = prefs.getString(KEY_DEFAULT_ALLOWED_PACKAGES, null) ?: return emptySet()
        val arr = JSONArray(raw)
        (0 until arr.length())
            .mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } }
            .toSet()
    }.getOrDefault(emptySet())

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

                    val remoteSchedulesRaw = snap.getString("dailyLockSchedulesJson")
                    val remoteSchedules = if (!remoteSchedulesRaw.isNullOrBlank()) {
                        runCatching {
                            val arr = JSONArray(remoteSchedulesRaw)
                            (0 until arr.length()).mapNotNull { i ->
                                runCatching { dailyScheduleFromJson(arr.getJSONObject(i)) }.getOrNull()
                            }
                        }.getOrDefault(emptyList())
                    } else emptyList()

                    val mergedHistory = (_state.value.history + remoteHistory)
                        .filterNot { it.isLockPhone }
                        .distinctBy { it.id }
                        .sortedByDescending { it.finishedAt ?: it.startAt }
                        .take(MAX_HISTORY)

                    val mergedSchedules = if (_state.value.dailySchedules.isEmpty() && remoteSchedules.isNotEmpty()) {
                        remoteSchedules
                    } else {
                        (_state.value.dailySchedules + remoteSchedules)
                            .distinctBy { it.id }
                            .sortedWith(compareBy<DailyLockSchedule> { it.startHour }.thenBy { it.startMinute })
                    }

                    if (mergedHistory != _state.value.history || mergedSchedules != _state.value.dailySchedules) {
                        val nextState = _state.value.copy(
                            history = mergedHistory,
                            dailySchedules = mergedSchedules
                        )
                        _state.value = nextState
                        prefs.edit()
                            .putString(KEY_HISTORY, JSONArray(mergedHistory.map { it.toJson() }).toString())
                            .putString(KEY_DAILY_SCHEDULES, JSONArray(mergedSchedules.map { it.toJson() }).toString())
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
            val historyJson = JSONArray(state.history.filterNot { it.isLockPhone }.take(MAX_HISTORY).map { it.toJson() }).toString()
            val schedulesJson = JSONArray(state.dailySchedules.map { it.toJson() }).toString()
            Firebase.firestore.collection("users").document(uid)
                .set(
                    mapOf(
                        "personalActiveChallengeJson" to activeJson,
                        "personalChallengeHistoryJson" to historyJson,
                        "personalChallengesDoneCount" to state.doneCount,
                        "dailyLockSchedulesJson" to schedulesJson
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
        quote: MotivationalQuote? = null,
        allowedPackages: Set<String>? = null
    ): Challenge {
        val now = clock()
        val historyWithPrevious = _state.value.active?.let { prev ->
            if (prev.isLockPhone) {
                _state.value.history
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
        val resolvedAllowed = if (type == PersonalChallengeType.LOCK_PHONE) {
            if (allowedPackages != null) {
                val cleaned = allowedPackages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                setDefaultAllowedPackages(cleaned)
                cleaned
            } else {
                getDefaultAllowedPackages()
            }
        } else {
            emptySet()
        }
        val challenge = Challenge(
            id = now,
            goal = cleanGoal,
            startAt = now,
            endAt = endAt,
            type = type,
            quoteText = chosenQuote?.quote.orEmpty(),
            quoteAuthor = chosenQuote?.author.orEmpty(),
            allowedPackages = resolvedAllowed
        )
        save(_state.value.copy(active = challenge, history = historyWithPrevious))
        return challenge
    }

    /** Ends the active challenge with [result]. Returns it, or null if none was active. */
    @Synchronized
    fun finish(result: ChallengeResult): Challenge? {
        val active = _state.value.active ?: return null
        val done = active.copy(finishedAt = clock(), result = result)
        val nextHistory = if (active.isLockPhone) {
            _state.value.history
        } else {
            upsertHistory(done, _state.value.history)
        }
        save(_state.value.copy(active = null, history = nextHistory))
        return done
    }

    /**
     * For [PersonalChallengeType.GOAL]: marks the active challenge failed when its countdown reaches 00:00,
     * recording it in history as FAILED while keeping it in [ChallengeState.active] so the overtime clock keeps counting up.
     *
     * For [PersonalChallengeType.LOCK_PHONE]: reaching 00:00 completes the lock session and unlocks the phone
     * (`active = null`) without adding a history record.
     */
    @Synchronized
    fun expireIfDue(): Challenge? {
        val active = _state.value.active ?: return null
        val now = clock()
        if (now < active.endAt) return null
        if (active.isLockPhone) {
            val done = active.copy(finishedAt = active.endAt, result = ChallengeResult.DONE)
            save(_state.value.copy(active = null))
            return done
        }
        if (active.result == ChallengeResult.FAILED) return active
        val failed = active.copy(finishedAt = now, result = ChallengeResult.FAILED)
        save(_state.value.copy(active = failed, history = upsertHistory(failed, _state.value.history)))
        return failed
    }

    /** Stops the overtime timer on an expired challenge and records total time spent in history. */
    @Synchronized
    fun stopOvertime(): Challenge? {
        val active = _state.value.active ?: return null
        val now = clock()
        val finalized = active.copy(finishedAt = maxOf(now, active.endAt), result = ChallengeResult.FAILED)
        val nextHistory = if (active.isLockPhone) {
            _state.value.history
        } else {
            upsertHistory(finalized, _state.value.history)
        }
        save(_state.value.copy(active = null, history = nextHistory))
        return finalized
    }

    fun clearHistory() = save(_state.value.copy(history = emptyList()))

    // ------------------------------------------------------------------ Daily Lock Schedules

    @Synchronized
    fun addDailySchedule(
        label: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        allowedPackages: Set<String>? = null
    ): DailyLockSchedule {
        val resolvedAllowed = if (allowedPackages != null) {
            val cleaned = allowedPackages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            setDefaultAllowedPackages(cleaned)
            cleaned
        } else {
            getDefaultAllowedPackages()
        }
        val schedule = DailyLockSchedule(
            id = "daily_${clock()}_${UUID.randomUUID().toString().take(6)}",
            label = label.trim(),
            startHour = startHour.coerceIn(0, 23),
            startMinute = startMinute.coerceIn(0, 59),
            endHour = endHour.coerceIn(0, 23),
            endMinute = endMinute.coerceIn(0, 59),
            enabled = true,
            allowedPackages = resolvedAllowed,
            snoozeUntilMs = 0L
        )
        val updated = (_state.value.dailySchedules + schedule)
            .sortedWith(compareBy<DailyLockSchedule> { it.startHour }.thenBy { it.startMinute })
        save(_state.value.copy(dailySchedules = updated))
        return schedule
    }

    @Synchronized
    fun setDailyScheduleEnabled(id: String, enabled: Boolean) {
        val updated = _state.value.dailySchedules.map { schedule ->
            if (schedule.id == id) {
                schedule.copy(enabled = enabled, snoozeUntilMs = if (enabled) schedule.snoozeUntilMs else 0L)
            } else {
                schedule
            }
        }
        save(_state.value.copy(dailySchedules = updated))
    }

    @Synchronized
    fun deleteDailySchedule(id: String) {
        val updated = _state.value.dailySchedules.filterNot { it.id == id }
        save(_state.value.copy(dailySchedules = updated))
    }

    /**
     * Unlimited `+1 min` snooze for an imminent daily lock schedule.
     * Advances the upcoming lock activation time by 60 seconds and returns the new lock timestamp.
     */
    @Synchronized
    fun snoozeDailyScheduleByOneMinute(
        id: String,
        nowMs: Long = clock(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        val target = _state.value.dailySchedules.firstOrNull { it.id == id && it.enabled } ?: return null
        val occ = DailyLockTime.nextOccurrence(target, nowMs, zone) ?: return null
        val newSnoozeUntil = maxOf(occ.effectiveStartMs, nowMs) + DailyLockTime.SNOOZE_STEP_MS
        val updated = _state.value.dailySchedules.map { schedule ->
            if (schedule.id == id) schedule.copy(snoozeUntilMs = newSnoozeUntil) else schedule
        }
        save(_state.value.copy(dailySchedules = updated))
        return newSnoozeUntil
    }

    @Synchronized
    fun clearDailyScheduleSnooze(id: String) {
        val updated = _state.value.dailySchedules.map { schedule ->
            if (schedule.id == id) schedule.copy(snoozeUntilMs = 0L) else schedule
        }
        save(_state.value.copy(dailySchedules = updated))
    }

    private fun upsertHistory(entry: Challenge, current: List<Challenge>): List<Challenge> {
        val cleanCurrent = current.filterNot { it.isLockPhone }
        if (entry.isLockPhone) return cleanCurrent
        val filtered = cleanCurrent.filterNot { it.id == entry.id }
        return (listOf(entry) + filtered).take(MAX_HISTORY)
    }

    private fun save(state: ChallengeState) {
        val cleanHistory = state.history.filterNot { it.isLockPhone }
        val cleanState = if (cleanHistory.size == state.history.size) state else state.copy(history = cleanHistory)
        _state.value = cleanState
        prefs.edit()
            .putString(KEY_ACTIVE, cleanState.active?.toJson()?.toString())
            .putString(KEY_HISTORY, JSONArray(cleanState.history.map { it.toJson() }).toString())
            .putString(KEY_DAILY_SCHEDULES, JSONArray(cleanState.dailySchedules.map { it.toJson() }).toString())
            .apply()
        val uid = runCatching { Firebase.auth.currentUser?.takeIf { !it.isAnonymous }?.uid.orEmpty() }.getOrDefault("")
        if (uid.isNotEmpty()) {
            pushToFirestore(uid, cleanState)
        }
    }

    private fun load(): ChallengeState = runCatching {
        val active = prefs.getString(KEY_ACTIVE, null)?.let { challengeFromJson(JSONObject(it)) }
        val array = JSONArray(prefs.getString(KEY_HISTORY, "[]"))
        val history = (0 until array.length())
            .map { challengeFromJson(array.getJSONObject(it)) }
            .filterNot { it.isLockPhone }
        val schedulesArr = JSONArray(prefs.getString(KEY_DAILY_SCHEDULES, "[]"))
        val schedules = (0 until schedulesArr.length())
            .mapNotNull { i -> runCatching { dailyScheduleFromJson(schedulesArr.getJSONObject(i)) }.getOrNull() }
            .sortedWith(compareBy<DailyLockSchedule> { it.startHour }.thenBy { it.startMinute })
        ChallengeState(active = active, history = history, dailySchedules = schedules)
    }.getOrDefault(ChallengeState())

    companion object {
        private const val PREFS = "challenges"
        private const val KEY_ACTIVE = "active"
        private const val KEY_HISTORY = "history"
        private const val KEY_DAILY_SCHEDULES = "daily_lock_schedules"
        private const val KEY_DEFAULT_ALLOWED_PACKAGES = "default_allowed_packages"
        private const val MAX_HISTORY = 200

        internal fun Challenge.toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("goal", goal)
            .put("startAt", startAt)
            .put("endAt", endAt)
            .put("type", type.name)
            .put("quoteText", quoteText)
            .put("quoteAuthor", quoteAuthor)
            .put("allowedPackages", JSONArray(allowedPackages.toList()))
            .apply {
                finishedAt?.let { put("finishedAt", it) }
                result?.let { put("result", it.name) }
            }

        internal fun challengeFromJson(o: JSONObject): Challenge {
            val parsedType = o.optString("type").takeIf { it.isNotEmpty() }?.let {
                runCatching { PersonalChallengeType.valueOf(it) }.getOrNull()
            } ?: PersonalChallengeType.GOAL
            val allowedArr = o.optJSONArray("allowedPackages")
            val parsedAllowed = if (allowedArr != null) {
                (0 until allowedArr.length())
                    .mapNotNull { i -> allowedArr.optString(i).trim().takeIf { it.isNotEmpty() } }
                    .toSet()
            } else emptySet()
            return Challenge(
                id = o.getLong("id"),
                goal = o.optString("goal"),
                startAt = o.getLong("startAt"),
                endAt = o.getLong("endAt"),
                finishedAt = if (o.has("finishedAt")) o.getLong("finishedAt") else null,
                result = o.optString("result").takeIf { it.isNotEmpty() }?.let { runCatching { ChallengeResult.valueOf(it) }.getOrNull() },
                type = parsedType,
                quoteText = o.optString("quoteText", ""),
                quoteAuthor = o.optString("quoteAuthor", ""),
                allowedPackages = parsedAllowed
            )
        }

        internal fun DailyLockSchedule.toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("label", label)
            .put("startHour", startHour)
            .put("startMinute", startMinute)
            .put("endHour", endHour)
            .put("endMinute", endMinute)
            .put("enabled", enabled)
            .put("allowedPackages", JSONArray(allowedPackages.toList()))
            .put("snoozeUntilMs", snoozeUntilMs)

        internal fun dailyScheduleFromJson(o: JSONObject): DailyLockSchedule {
            val allowedArr = o.optJSONArray("allowedPackages")
            val parsedAllowed = if (allowedArr != null) {
                (0 until allowedArr.length())
                    .mapNotNull { i -> allowedArr.optString(i).trim().takeIf { it.isNotEmpty() } }
                    .toSet()
            } else emptySet()
            return DailyLockSchedule(
                id = o.optString("id").ifBlank { "daily_${System.currentTimeMillis()}" },
                label = o.optString("label", ""),
                startHour = o.optInt("startHour", 22).coerceIn(0, 23),
                startMinute = o.optInt("startMinute", 0).coerceIn(0, 59),
                endHour = o.optInt("endHour", 7).coerceIn(0, 23),
                endMinute = o.optInt("endMinute", 0).coerceIn(0, 59),
                enabled = o.optBoolean("enabled", true),
                allowedPackages = parsedAllowed,
                snoozeUntilMs = o.optLong("snoozeUntilMs", 0L)
            )
        }
    }
}

