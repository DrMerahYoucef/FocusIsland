package com.example.data.challenge

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.db.dao.SessionDao
import com.example. data.stats.StatsCalculator
import com.example.service.Phase
import com.example.service.PomodoroTimerService
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

enum class FriendChallengeStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    SURRENDERED,
    DECLINED;

    companion object {
        fun from(raw: String?): FriendChallengeStatus = when (raw?.lowercase()) {
            "active" -> ACTIVE
            "completed" -> COMPLETED
            "surrendered" -> SURRENDERED
            "declined" -> DECLINED
            else -> PENDING
        }
    }
}

/**
 * A head-to-head daily study challenge between two friends: whoever studies more minutes
 * before midnight wins, unless one player surrenders early (making the other player the immediate winner).
 */
data class FriendChallenge(
    val id: String,
    val challengerUid: String,
    val challengerName: String,
    val challengerMinutes: Int,
    val opponentUid: String,
    val opponentName: String,
    val opponentMinutes: Int,
    val dayKey: String,
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val endAt: Long,
    val finishedAt: Long? = null,
    val status: FriendChallengeStatus = FriendChallengeStatus.PENDING,
    val surrenderedByUid: String? = null,
    val winnerUid: String? = null
) {
    val participants: List<String> get() = listOf(challengerUid, opponentUid)

    fun isChallenger(uid: String): Boolean = challengerUid == uid
    fun friendUid(myUid: String): String = if (isChallenger(myUid)) opponentUid else challengerUid
    fun friendName(myUid: String): String = if (isChallenger(myUid)) opponentName else challengerName
    fun myName(myUid: String): String = if (isChallenger(myUid)) challengerName else opponentName
    fun myMinutes(myUid: String): Int = if (isChallenger(myUid)) challengerMinutes else opponentMinutes
    fun friendMinutes(myUid: String): Int = if (isChallenger(myUid)) opponentMinutes else challengerMinutes

    fun didIWin(myUid: String): Boolean = myUid.isNotEmpty() && winnerUid == myUid
    fun didISurrender(myUid: String): Boolean = status == FriendChallengeStatus.SURRENDERED && surrenderedByUid == myUid
    fun didFriendSurrender(myUid: String): Boolean =
        status == FriendChallengeStatus.SURRENDERED && !surrenderedByUid.isNullOrEmpty() && surrenderedByUid != myUid
}

data class FriendChallengeState(
    val myUid: String = "",
    val myLiveMinutesToday: Int = 0,
    val incomingPending: List<FriendChallenge> = emptyList(),
    val outgoingPending: List<FriendChallenge> = emptyList(),
    val activeDuel: FriendChallenge? = null,
    val history: List<FriendChallenge> = emptyList(),
    val uncelebratedWin: FriendChallenge? = null
)

class FriendChallengeRepository(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(FriendChallengeState())
    val state: StateFlow<FriendChallengeState> = _state.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var currentObservedUid: String = ""

    private val celebratedIds: MutableSet<String> =
        (prefs.getStringSet(KEY_CELEBRATED_IDS, emptySet()) ?: emptySet()).toMutableSet()
    private val autoStartedIds: MutableSet<String> =
        (prefs.getStringSet(KEY_AUTO_STARTED_IDS, emptySet()) ?: emptySet()).toMutableSet()

    init {
        val cached = loadCachedChallenges()
        if (cached.isNotEmpty()) {
            rebuildState(cached, currentUid(), _state.value.myLiveMinutesToday)
        }
        observeAuthAndChallenges()
        observeTodayFocusMinutes()
        startMidnightWatcher()
    }

    private fun currentUid(): String = runCatching {
        Firebase.auth.currentUser?.takeIf { !it.isAnonymous }?.uid.orEmpty()
    }.getOrDefault("")

    private fun observeAuthAndChallenges() {
        runCatching {
            Firebase.auth.addAuthStateListener(FirebaseAuth.AuthStateListener { auth ->
                val uid = auth.currentUser?.takeIf { !it.isAnonymous }?.uid.orEmpty()
                if (uid != currentObservedUid) {
                    currentObservedUid = uid
                    attachFirestoreListener(uid)
                }
            })
            val initialUid = currentUid()
            currentObservedUid = initialUid
            attachFirestoreListener(initialUid)
        }.onFailure {
            Log.w(TAG, "Auth listener setup skipped: ${it.message}")
        }
    }

    private fun attachFirestoreListener(uid: String) {
        listenerRegistration?.remove()
        listenerRegistration = null
        if (uid.isEmpty()) {
            rebuildState(loadCachedChallenges(), "", _state.value.myLiveMinutesToday)
            return
        }
        runCatching {
            val db = Firebase.firestore
            listenerRegistration = db.collection(COLLECTION)
                .whereArrayContains("participants", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Friend challenges listener failed: ${error.message}")
                        return@addSnapshotListener
                    }
                    val remote = snapshot?.documents?.mapNotNull { it.toFriendChallenge() }.orEmpty()
                    saveCachedChallenges(remote)
                    rebuildState(remote, uid, _state.value.myLiveMinutesToday)
                }
        }.onFailure {
            Log.w(TAG, "Failed to attach Firestore listener: ${it.message}")
        }
    }

    /**
     * Tracks today's completed focus minutes + live running Pomodoro focus minutes,
     * and pushes updates to the active friend challenge whenever the minute total changes.
     */
    private fun observeTodayFocusMinutes() {
        appScope.launch {
            val completedMinutesFlow = sessionDao.getAllSessions().map { all ->
                val today = LocalDate.now()
                all.filter { it.completed && StatsCalculator.localDay(it.date) == today }
                    .sumOf { it.durationSeconds } / 60
            }
            val liveRunningMinutesFlow = PomodoroTimerService.state.map { timer ->
                if (timer.phase == Phase.FOCUS && !timer.isUntouched) {
                    ((timer.phaseDurationMs - timer.remainingMs).coerceAtLeast(0L) / 60_000L).toInt()
                } else 0
            }.distinctUntilChanged()

            combine(completedMinutesFlow, liveRunningMinutesFlow) { completed, live ->
                completed + live
            }.distinctUntilChanged().collectLatest { totalTodayMinutes ->
                _state.value = _state.value.copy(myLiveMinutesToday = totalTodayMinutes)
                val active = _state.value.activeDuel ?: return@collectLatest
                val uid = _state.value.myUid.ifEmpty { currentUid() }
                if (uid.isNotEmpty() && active.myMinutes(uid) != totalTodayMinutes) {
                    pushMyMinutesToActiveChallenge(active, uid, totalTodayMinutes)
                }
            }
        }
    }

    private fun startMidnightWatcher() {
        appScope.launch {
            while (isActive) {
                val active = _state.value.activeDuel
                val now = clock()
                if (active != null && now >= active.endAt) {
                    completeExpiredChallenge(active)
                }
                delay(5_000L)
            }
        }
    }

    private fun rebuildState(all: List<FriendChallenge>, uid: String, liveMinutes: Int) {
        val effectiveUid = uid.ifEmpty { _state.value.myUid }
        val now = clock()

        // Check if any active challenge has already passed midnight
        all.filter { it.status == FriendChallengeStatus.ACTIVE && now >= it.endAt }.forEach { expired ->
            appScope.launch { completeExpiredChallenge(expired) }
        }

        val incoming = all.filter {
            it.status == FriendChallengeStatus.PENDING && it.opponentUid == effectiveUid && now < it.endAt
        }.sortedByDescending { it.createdAt }

        val outgoing = all.filter {
            it.status == FriendChallengeStatus.PENDING && it.challengerUid == effectiveUid && now < it.endAt
        }.sortedByDescending { it.createdAt }

        val activeRaw = all.filter {
            it.status == FriendChallengeStatus.ACTIVE && now < it.endAt
        }.maxByOrNull { it.acceptedAt ?: it.createdAt }

        // Overlay our live local minutes onto the active duel for instant UI responsiveness
        val active = activeRaw?.let { duel ->
            if (effectiveUid.isNotEmpty()) {
                if (duel.isChallenger(effectiveUid)) {
                    duel.copy(challengerMinutes = maxOf(duel.challengerMinutes, liveMinutes))
                } else {
                    duel.copy(opponentMinutes = maxOf(duel.opponentMinutes, liveMinutes))
                }
            } else duel
        }

        // Auto-start the Pomodoro timer the first time a challenge becomes ACTIVE for this user!
        if (active != null && active.id !in autoStartedIds) {
            markAutoStarted(active.id)
            startPomodoroForActiveChallenge()
            if (effectiveUid.isNotEmpty() && liveMinutes > active.myMinutes(effectiveUid)) {
                appScope.launch { pushMyMinutesToActiveChallenge(active, effectiveUid, liveMinutes) }
            }
        }

        val history = all.filter {
            it.status == FriendChallengeStatus.COMPLETED || it.status == FriendChallengeStatus.SURRENDERED
        }.sortedByDescending { it.finishedAt ?: it.endAt }

        val uncelebrated = history.firstOrNull { ch ->
            effectiveUid.isNotEmpty() && ch.didIWin(effectiveUid) && ch.id !in celebratedIds
        }

        _state.value = FriendChallengeState(
            myUid = effectiveUid,
            myLiveMinutesToday = liveMinutes,
            incomingPending = incoming,
            outgoingPending = outgoing,
            activeDuel = active,
            history = history,
            uncelebratedWin = uncelebrated
        )
    }

    private fun startPomodoroForActiveChallenge() {
        runCatching {
            if (!PomodoroTimerService.isRunning) {
                val intent = Intent(context, PomodoroTimerService::class.java)
                    .setAction(PomodoroTimerService.ACTION_START)
                ContextCompat.startForegroundService(context, intent)
            }
        }.onFailure {
            Log.w(TAG, "Could not auto-start Pomodoro timer for challenge: ${it.message}")
        }
    }

    private fun markAutoStarted(challengeId: String) {
        autoStartedIds.add(challengeId)
        prefs.edit().putStringSet(KEY_AUTO_STARTED_IDS, autoStartedIds.toSet()).apply()
    }

    fun acknowledgeCelebration(challengeId: String) {
        celebratedIds.add(challengeId)
        prefs.edit().putStringSet(KEY_CELEBRATED_IDS, celebratedIds.toSet()).apply()
        if (_state.value.uncelebratedWin?.id == challengeId) {
            _state.value = _state.value.copy(uncelebratedWin = null)
        }
    }

    /**
     * Sends a daily study challenge to [opponentUid].
     * Ends at midnight tonight in the user's timezone.
     */
    suspend fun sendChallenge(
        challengerName: String,
        opponentUid: String,
        opponentName: String,
        opponentInitialMinutes: Int = 0
    ): Result<FriendChallenge> = runCatching {
        val myUid = currentUid().ifEmpty { "local_user" }
        val now = clock()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayKey = today.toString()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val myMins = _state.value.myLiveMinutesToday
        val id = "${dayKey}_${myUid}_$opponentUid"

        val challenge = FriendChallenge(
            id = id,
            challengerUid = myUid,
            challengerName = challengerName.ifBlank { "You" },
            challengerMinutes = myMins,
            opponentUid = opponentUid,
            opponentName = opponentName.ifBlank { "Friend" },
            opponentMinutes = opponentInitialMinutes,
            dayKey = dayKey,
            createdAt = now,
            acceptedAt = null,
            endAt = midnight,
            finishedAt = null,
            status = FriendChallengeStatus.PENDING
        )

        upsertLocalCache(challenge)
        if (currentUid().isNotEmpty()) {
            Firebase.firestore.collection(COLLECTION)
                .document(id)
                .set(challenge.toFirestoreMap())
                .await()
        }
        challenge
    }

    /**
     * Accepts an incoming challenge, transitions it to ACTIVE, and starts the Pomodoro timer.
     */
    suspend fun acceptChallenge(challenge: FriendChallenge): Result<FriendChallenge> = runCatching {
        val now = clock()
        val myMins = _state.value.myLiveMinutesToday
        val updated = challenge.copy(
            status = FriendChallengeStatus.ACTIVE,
            acceptedAt = now,
            opponentMinutes = maxOf(challenge.opponentMinutes, myMins)
        )
        upsertLocalCache(updated)
        if (currentUid().isNotEmpty()) {
            Firebase.firestore.collection(COLLECTION)
                .document(challenge.id)
                .update(
                    mapOf(
                        "status" to FriendChallengeStatus.ACTIVE.name.lowercase(),
                        "acceptedAt" to now,
                        "opponentMinutes" to updated.opponentMinutes
                    )
                )
                .await()
        }
        updated
    }

    suspend fun declineChallenge(challenge: FriendChallenge): Result<Unit> = runCatching {
        val updated = challenge.copy(status = FriendChallengeStatus.DECLINED, finishedAt = clock())
        upsertLocalCache(updated)
        if (currentUid().isNotEmpty()) {
            Firebase.firestore.collection(COLLECTION)
                .document(challenge.id)
                .update(
                    mapOf(
                        "status" to FriendChallengeStatus.DECLINED.name.lowercase(),
                        "finishedAt" to updated.finishedAt
                    )
                )
                .await()
        }
    }

    suspend fun cancelOutgoingChallenge(challenge: FriendChallenge): Result<Unit> = runCatching {
        val current = loadCachedChallenges().filterNot { it.id == challenge.id }
        saveCachedChallenges(current)
        rebuildState(current, _state.value.myUid, _state.value.myLiveMinutesToday)
        if (currentUid().isNotEmpty()) {
            Firebase.firestore.collection(COLLECTION)
                .document(challenge.id)
                .delete()
                .await()
        }
    }

    /**
     * Surrenders the active challenge: the other friend immediately wins and gets the celebration.
     */
    suspend fun surrender(challenge: FriendChallenge): Result<FriendChallenge> = runCatching {
        val myUid = _state.value.myUid.ifEmpty { currentUid().ifEmpty { challenge.challengerUid } }
        val winner = challenge.friendUid(myUid)
        val now = clock()
        val updated = challenge.copy(
            status = FriendChallengeStatus.SURRENDERED,
            surrenderedByUid = myUid,
            winnerUid = winner,
            finishedAt = now
        )
        upsertLocalCache(updated)
        if (currentUid().isNotEmpty()) {
            Firebase.firestore.collection(COLLECTION)
                .document(challenge.id)
                .update(
                    mapOf(
                        "status" to FriendChallengeStatus.SURRENDERED.name.lowercase(),
                        "surrenderedByUid" to myUid,
                        "winnerUid" to winner,
                        "finishedAt" to now
                    )
                )
                .await()
        }
        updated
    }

    private suspend fun pushMyMinutesToActiveChallenge(challenge: FriendChallenge, myUid: String, minutes: Int) {
        val updated = if (challenge.isChallenger(myUid)) {
            challenge.copy(challengerMinutes = minutes)
        } else {
            challenge.copy(opponentMinutes = minutes)
        }
        upsertLocalCache(updated)
        if (currentUid().isNotEmpty()) {
            val field = if (challenge.isChallenger(myUid)) "challengerMinutes" else "opponentMinutes"
            runCatching {
                Firebase.firestore.collection(COLLECTION)
                    .document(challenge.id)
                    .set(mapOf(field to minutes), SetOptions.merge())
                    .await()
            }.onFailure {
                Log.d(TAG, "Live minutes sync skipped: ${it.message}")
            }
        }
    }

    private suspend fun completeExpiredChallenge(challenge: FriendChallenge) {
        val winner = when {
            challenge.challengerMinutes > challenge.opponentMinutes -> challenge.challengerUid
            challenge.opponentMinutes > challenge.challengerMinutes -> challenge.opponentUid
            else -> "tie"
        }
        val finishedAt = challenge.endAt
        val updated = challenge.copy(
            status = FriendChallengeStatus.COMPLETED,
            winnerUid = winner,
            finishedAt = finishedAt
        )
        upsertLocalCache(updated)
        if (currentUid().isNotEmpty()) {
            runCatching {
                Firebase.firestore.collection(COLLECTION)
                    .document(challenge.id)
                    .update(
                        mapOf(
                            "status" to FriendChallengeStatus.COMPLETED.name.lowercase(),
                            "winnerUid" to winner,
                            "finishedAt" to finishedAt
                        )
                    )
                    .await()
            }
        }
    }

    private fun upsertLocalCache(challenge: FriendChallenge) {
        val existing = loadCachedChallenges().toMutableList()
        val idx = existing.indexOfFirst { it.id == challenge.id }
        if (idx >= 0) existing[idx] = challenge else existing.add(0, challenge)
        saveCachedChallenges(existing)
        rebuildState(existing, _state.value.myUid.ifEmpty { currentUid() }, _state.value.myLiveMinutesToday)
    }

    private fun loadCachedChallenges(): List<FriendChallenge> = runCatching {
        val raw = prefs.getString(KEY_CACHE, "[]") ?: "[]"
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            runCatching { challengeFromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun saveCachedChallenges(list: List<FriendChallenge>) {
        val arr = JSONArray(list.take(MAX_CACHE).map { it.toJson() })
        prefs.edit().putString(KEY_CACHE, arr.toString()).apply()
    }

    companion object {
        private const val TAG = "FriendChallengeRepo"
        private const val COLLECTION = "friend_challenges"
        private const val PREFS_NAME = "friend_challenges_prefs"
        private const val KEY_CACHE = "cached_challenges"
        private const val KEY_CELEBRATED_IDS = "celebrated_ids"
        private const val KEY_AUTO_STARTED_IDS = "auto_started_ids"
        private const val MAX_CACHE = 100

        private fun DocumentSnapshot.toFriendChallenge(): FriendChallenge? = runCatching {
            val challengerUid = getString("challengerUid") ?: return null
            val opponentUid = getString("opponentUid") ?: return null
            FriendChallenge(
                id = id,
                challengerUid = challengerUid,
                challengerName = getString("challengerName").orEmpty(),
                challengerMinutes = (getLong("challengerMinutes") ?: 0L).toInt(),
                opponentUid = opponentUid,
                opponentName = getString("opponentName").orEmpty(),
                opponentMinutes = (getLong("opponentMinutes") ?: 0L).toInt(),
                dayKey = getString("dayKey").orEmpty(),
                createdAt = getLong("createdAt") ?: 0L,
                acceptedAt = getLong("acceptedAt"),
                endAt = getLong("endAt") ?: 0L,
                finishedAt = getLong("finishedAt"),
                status = FriendChallengeStatus.from(getString("status")),
                surrenderedByUid = getString("surrenderedByUid"),
                winnerUid = getString("winnerUid")
            )
        }.getOrNull()

        private fun FriendChallenge.toFirestoreMap(): Map<String, Any?> = mapOf(
            "participants" to participants,
            "challengerUid" to challengerUid,
            "challengerName" to challengerName,
            "challengerMinutes" to challengerMinutes,
            "opponentUid" to opponentUid,
            "opponentName" to opponentName,
            "opponentMinutes" to opponentMinutes,
            "dayKey" to dayKey,
            "createdAt" to createdAt,
            "acceptedAt" to acceptedAt,
            "endAt" to endAt,
            "finishedAt" to finishedAt,
            "status" to status.name.lowercase(),
            "surrenderedByUid" to surrenderedByUid,
            "winnerUid" to winnerUid
        )

        internal fun FriendChallenge.toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("challengerUid", challengerUid)
            .put("challengerName", challengerName)
            .put("challengerMinutes", challengerMinutes)
            .put("opponentUid", opponentUid)
            .put("opponentName", opponentName)
            .put("opponentMinutes", opponentMinutes)
            .put("dayKey", dayKey)
            .put("createdAt", createdAt)
            .put("endAt", endAt)
            .put("status", status.name.lowercase())
            .apply {
                acceptedAt?.let { put("acceptedAt", it) }
                finishedAt?.let { put("finishedAt", it) }
                surrenderedByUid?.let { put("surrenderedByUid", it) }
                winnerUid?.let { put("winnerUid", it) }
            }

        internal fun challengeFromJson(o: JSONObject): FriendChallenge = FriendChallenge(
            id = o.getString("id"),
            challengerUid = o.getString("challengerUid"),
            challengerName = o.optString("challengerName"),
            challengerMinutes = o.optInt("challengerMinutes", 0),
            opponentUid = o.getString("opponentUid"),
            opponentName = o.optString("opponentName"),
            opponentMinutes = o.optInt("opponentMinutes", 0),
            dayKey = o.optString("dayKey"),
            createdAt = o.optLong("createdAt", 0L),
            acceptedAt = if (o.has("acceptedAt")) o.getLong("acceptedAt") else null,
            endAt = o.optLong("endAt", 0L),
            finishedAt = if (o.has("finishedAt")) o.getLong("finishedAt") else null,
            status = FriendChallengeStatus.from(o.optString("status")),
            surrenderedByUid = o.optString("surrenderedByUid").takeIf { it.isNotEmpty() },
            winnerUid = o.optString("winnerUid").takeIf { it.isNotEmpty() }
        )
    }
}
