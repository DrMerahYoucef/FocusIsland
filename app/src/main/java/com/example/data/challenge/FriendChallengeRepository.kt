package com.example.data.challenge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.dao.SessionDao
import com.example.data.stats.StatsCalculator
import com.example.service.Phase
import com.example.service.PomodoroTimerService
import com.example.ui.designsystem.playSuccessSound
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
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
    DECLINED,
    CANCELLED;

    companion object {
        fun from(raw: String?): FriendChallengeStatus = when (raw?.lowercase()) {
            "active" -> ACTIVE
            "completed" -> COMPLETED
            "surrendered" -> SURRENDERED
            "declined" -> DECLINED
            "cancelled", "deleted" -> CANCELLED
            else -> PENDING
        }
    }
}

enum class StudyMemberTimerState {
    FOCUSING,
    ON_BREAK,
    PAUSED,
    IDLE;

    companion object {
        fun from(raw: String?): StudyMemberTimerState = when (raw?.lowercase()) {
            "focusing", "running" -> FOCUSING
            "on_break", "break", "short_break", "long_break" -> ON_BREAK
            "paused" -> PAUSED
            else -> IDLE
        }
    }
}

enum class StudyMemberRoleStatus {
    ACTIVE,
    INVITED,
    DECLINED,
    SURRENDERED;

    companion object {
        fun from(raw: String?): StudyMemberRoleStatus = when (raw?.lowercase()) {
            "invited", "pending" -> INVITED
            "declined" -> DECLINED
            "surrendered", "left" -> SURRENDERED
            else -> ACTIVE
        }
    }
}

data class StudyGroupMember(
    val uid: String,
    val name: String,
    val minutes: Int = 0,
    val timerState: StudyMemberTimerState = StudyMemberTimerState.IDLE,
    val roleStatus: StudyMemberRoleStatus = StudyMemberRoleStatus.ACTIVE,
    val invitedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val baselineMinutes: Int = 0
) {
    val isFocusing: Boolean get() = timerState == StudyMemberTimerState.FOCUSING
    val isOnBreak: Boolean get() = timerState == StudyMemberTimerState.ON_BREAK
    val isPausedOrIdle: Boolean get() = timerState == StudyMemberTimerState.PAUSED || timerState == StudyMemberTimerState.IDLE

    fun challengeMinutesFromToday(todayTotalMinutes: Int): Int =
        (todayTotalMinutes - baselineMinutes.coerceAtLeast(0)).coerceAtLeast(0)

    fun effectiveInvitedAt(challengeCreatedAt: Long = 0L): Long = when {
        invitedAt > 0L -> invitedAt
        updatedAt > 0L -> updatedAt
        else -> challengeCreatedAt
    }

    fun isInviteValid(now: Long, challengeCreatedAt: Long = 0L): Boolean =
        (roleStatus == StudyMemberRoleStatus.INVITED || roleStatus == StudyMemberRoleStatus.DECLINED) &&
            (now - effectiveInvitedAt(challengeCreatedAt)) < FriendChallengeRepository.INVITE_EXPIRY_MS

    fun isInviteExpired(now: Long, challengeCreatedAt: Long = 0L): Boolean =
        (roleStatus == StudyMemberRoleStatus.INVITED || roleStatus == StudyMemberRoleStatus.DECLINED) &&
            (now - effectiveInvitedAt(challengeCreatedAt)) >= FriendChallengeRepository.INVITE_EXPIRY_MS

    fun inviteRemainingMs(now: Long, challengeCreatedAt: Long = 0L): Long =
        (effectiveInvitedAt(challengeCreatedAt) + FriendChallengeRepository.INVITE_EXPIRY_MS - now).coerceAtLeast(0L)
}

data class StudyGroupMessage(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val text: String,
    val createdAt: Long,
    val isNudgeAlert: Boolean = false
)

data class StudyGroupNudge(
    val id: String,
    val challengeId: String,
    val fromUid: String,
    val fromName: String,
    val toUid: String,
    val toName: String,
    val createdAt: Long
)

data class StudyFriendOption(
    val uid: String,
    val username: String,
    val todayMinutes: Int = 0,
    val timerState: StudyMemberTimerState = StudyMemberTimerState.IDLE
)

/**
 * A daily study challenge / Study Group between two or more friends.
 * Supports 1v1 and multi-friend study groups, real-time progress, timer status (focusing/paused),
 * custom messages & quick emojis, and sound+vibration focus nudges.
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
    val winnerUid: String? = null,
    val members: List<StudyGroupMember> = emptyList(),
    val messages: List<StudyGroupMessage> = emptyList(),
    val nudges: List<StudyGroupNudge> = emptyList(),
    val messagesClearedAt: Long = 0L,
    val targetMinutes: Int = 0
) {
    val isTargetHoursChallenge: Boolean get() = targetMinutes > 0

    /**
     * In a Target-Hours Race (`targetMinutes > 0`), returns the UID of the accepted participant
     * who reached [targetMinutes] first once at least 2 participants have joined (or after creation).
     */
    fun findTargetReachedWinnerUid(): String? {
        if (targetMinutes <= 0) return null
        val accepted = acceptedMembers()
        val qualified = accepted.filter { m ->
            m.minutes >= targetMinutes && (accepted.size >= 2 || m.updatedAt > createdAt)
        }
        return qualified
            .sortedWith(
                compareBy<StudyGroupMember> { if (it.updatedAt > 0L) it.updatedAt else Long.MAX_VALUE }
                    .thenByDescending { it.minutes }
            )
            .firstOrNull()
            ?.uid
    }
    fun allMembers(): List<StudyGroupMember> {
        if (members.isNotEmpty()) return members
        return buildList {
            if (challengerUid.isNotBlank()) {
                add(
                    StudyGroupMember(
                        uid = challengerUid,
                        name = challengerName.ifBlank { "Leader" },
                        minutes = challengerMinutes,
                        roleStatus = if (surrenderedByUid == challengerUid) StudyMemberRoleStatus.SURRENDERED else StudyMemberRoleStatus.ACTIVE
                    )
                )
            }
            if (opponentUid.isNotBlank() && opponentUid != challengerUid) {
                add(
                    StudyGroupMember(
                        uid = opponentUid,
                        name = opponentName.ifBlank { "Friend" },
                        minutes = opponentMinutes,
                        roleStatus = when {
                            surrenderedByUid == opponentUid -> StudyMemberRoleStatus.SURRENDERED
                            status == FriendChallengeStatus.PENDING -> StudyMemberRoleStatus.INVITED
                            status == FriendChallengeStatus.DECLINED -> StudyMemberRoleStatus.DECLINED
                            else -> StudyMemberRoleStatus.ACTIVE
                        },
                        invitedAt = createdAt
                    )
                )
            }
        }
    }

    fun acceptedMembers(): List<StudyGroupMember> =
        allMembers().filter { it.roleStatus == StudyMemberRoleStatus.ACTIVE }

    fun pendingOrExpiredInviteMembers(): List<StudyGroupMember> =
        allMembers().filter {
            it.roleStatus == StudyMemberRoleStatus.INVITED || it.roleStatus == StudyMemberRoleStatus.DECLINED
        }

    fun activeAndInvitedMembers(): List<StudyGroupMember> =
        allMembers().filter { it.roleStatus != StudyMemberRoleStatus.SURRENDERED }

    val participants: List<String>
        get() = (listOf(challengerUid, opponentUid) + members.map { it.uid })
            .filter { it.isNotBlank() }
            .distinct()

    fun hasActiveParticipant(uid: String): Boolean =
        allMembers().any { it.uid == uid && it.roleStatus == StudyMemberRoleStatus.ACTIVE }

    fun hasValidInvite(uid: String, now: Long): Boolean =
        allMembers().any { it.uid == uid && it.isInviteValid(now, createdAt) }

    fun isFreshInviteFor(uid: String, now: Long): Boolean =
        allMembers().any {
            it.uid == uid && it.roleStatus == StudyMemberRoleStatus.INVITED && it.isInviteValid(now, createdAt)
        }

    fun isDeclinedOfferFor(uid: String, now: Long): Boolean =
        allMembers().any {
            it.uid == uid && it.roleStatus == StudyMemberRoleStatus.DECLINED && it.isInviteValid(now, createdAt)
        }

    fun inviteRemainingMsFor(uid: String, now: Long): Long =
        allMembers().firstOrNull { it.uid == uid }?.inviteRemainingMs(now, createdAt) ?: 0L

    fun isInvited(uid: String): Boolean =
        allMembers().any {
            it.uid == uid && (it.roleStatus == StudyMemberRoleStatus.INVITED || it.roleStatus == StudyMemberRoleStatus.DECLINED)
        }

    fun isChallenger(uid: String): Boolean = challengerUid == uid

    fun friendUid(myUid: String): String {
        val other = activeAndInvitedMembers().firstOrNull { it.uid != myUid }?.uid
        return other ?: if (isChallenger(myUid)) opponentUid else challengerUid
    }

    fun friendName(myUid: String): String {
        val others = activeAndInvitedMembers().filter { it.uid != myUid }
        if (others.size > 1) {
            val first = others.first().name.ifBlank { "Friend" }
            return "$first +${others.size - 1}"
        }
        return others.firstOrNull()?.name?.takeIf { it.isNotBlank() }
            ?: if (isChallenger(myUid)) opponentName else challengerName
    }

    fun myName(myUid: String): String =
        allMembers().firstOrNull { it.uid == myUid }?.name?.takeIf { it.isNotBlank() }
            ?: if (isChallenger(myUid)) challengerName else opponentName

    fun myMinutes(myUid: String): Int =
        allMembers().firstOrNull { it.uid == myUid }?.minutes
            ?: if (isChallenger(myUid)) challengerMinutes else opponentMinutes

    fun friendMinutes(myUid: String): Int {
        val others = activeAndInvitedMembers().filter { it.uid != myUid }
        return if (others.isNotEmpty()) {
            others.maxOf { it.minutes }
        } else {
            if (isChallenger(myUid)) opponentMinutes else challengerMinutes
        }
    }

    fun didIWin(myUid: String): Boolean = myUid.isNotEmpty() && winnerUid == myUid
    fun didISurrender(myUid: String): Boolean =
        (status == FriendChallengeStatus.SURRENDERED && surrenderedByUid == myUid) ||
            allMembers().any { it.uid == myUid && it.roleStatus == StudyMemberRoleStatus.SURRENDERED }

    fun didFriendSurrender(myUid: String): Boolean =
        status == FriendChallengeStatus.SURRENDERED && !surrenderedByUid.isNullOrEmpty() && surrenderedByUid != myUid

    /**
     * Returns all challenge participants ordered by final rank (1st, 2nd, 3rd, and the rest).
     * Non-surrendered active participants rank above surrendered/declined participants, ordered by
     * explicit winner first, then highest focus minutes, then earliest completion timestamp.
     */
    fun rankedParticipants(): List<StudyGroupMember> {
        val list = allMembers()
        if (list.isEmpty()) return emptyList()
        val explicitWinner = winnerUid?.takeIf { it.isNotBlank() && it != "tie" && it != "none" }
        return list.sortedWith(
            compareBy<StudyGroupMember> { m ->
                when {
                    explicitWinner != null && m.uid == explicitWinner -> 0
                    m.roleStatus == StudyMemberRoleStatus.ACTIVE -> 1
                    m.roleStatus == StudyMemberRoleStatus.INVITED -> 2
                    m.roleStatus == StudyMemberRoleStatus.SURRENDERED -> 3
                    else -> 4
                }
            }
                .thenByDescending { it.minutes }
                .thenBy { if (it.updatedAt > 0L) it.updatedAt else Long.MAX_VALUE }
                .thenBy { it.name.lowercase() }
        )
    }
}

data class FriendChallengeState(
    val myUid: String = "",
    val myName: String = "",
    val myLiveMinutesToday: Int = 0,
    val myTimerState: StudyMemberTimerState = StudyMemberTimerState.IDLE,
    val incomingPending: List<FriendChallenge> = emptyList(),
    val outgoingPending: List<FriendChallenge> = emptyList(),
    val activeDuel: FriendChallenge? = null,
    val history: List<FriendChallenge> = emptyList(),
    val uncelebratedWin: FriendChallenge? = null,
    val availableFriends: List<StudyFriendOption> = emptyList(),
    val nudgeCooldownUntil: Map<String, Long> = emptyMap(),
    val activeNudgeBanner: StudyGroupNudge? = null,
    val challengesWonCount: Int = 0
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

    private val listenerRegistrations = java.util.concurrent.CopyOnWriteArrayList<ListenerRegistration>()
    private val friendDocListeners = java.util.concurrent.ConcurrentHashMap<String, ListenerRegistration>()
    private val remoteUserDuels = java.util.concurrent.ConcurrentHashMap<String, FriendChallenge>()
    private val remoteCollectionDuels = java.util.concurrent.ConcurrentHashMap<String, FriendChallenge>()
    private val liveFriendProfiles = java.util.concurrent.ConcurrentHashMap<String, StudyFriendOption>()

    @Volatile
    private var currentObservedUid: String = ""
    @Volatile
    private var myResolvedUsername: String = ""
    @Volatile
    private var myFriendIds: Set<String> = emptySet()
    @Volatile
    private var todayCompletedSessions: List<com.example.data.db.entity.SessionEntity> = emptyList()

    private fun getPreChallengeMinutes(challengeCreatedAt: Long): Int {
        if (challengeCreatedAt <= 0L) return 0
        return todayCompletedSessions
            .filter { it.date <= challengeCreatedAt }
            .sumOf { it.durationSeconds } / 60
    }

    private val celebratedIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_CELEBRATED_IDS, emptySet()) ?: emptySet()).toMutableSet()
    )
    private val autoStartedIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_AUTO_STARTED_IDS, emptySet()) ?: emptySet()).toMutableSet()
    )
    private val abandonedIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_ABANDONED_IDS, emptySet()) ?: emptySet()).toMutableSet()
    )
    private val handledNudgeIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_HANDLED_NUDGE_IDS, emptySet()) ?: emptySet()).toMutableSet()
    )
    private val handledInviteIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_HANDLED_INVITE_IDS, emptySet()) ?: emptySet()).toMutableSet()
    )
    private val handledMessageIds: MutableSet<String> = java.util.Collections.synchronizedSet(
        (prefs.getStringSet(KEY_HANDLED_MESSAGE_IDS, emptySet()) ?: emptySet()).toMutableSet()
    )

    init {
        createNudgeNotificationChannel()
        createInviteNotificationChannel()
        createMessageNotificationChannel()
        val cached = loadCachedChallenges()
        if (cached.isNotEmpty()) {
            val existingMsgIds = cached.flatMap { ch -> ch.messages.map { it.id } }.filter { it.isNotBlank() }
            if (existingMsgIds.isNotEmpty()) {
                synchronized(handledMessageIds) {
                    handledMessageIds.addAll(existingMsgIds)
                }
            }
            rebuildState(cached, currentUid(), _state.value.myLiveMinutesToday)
        }
        observeAuthAndChallenges()
        observeTodayFocusAndTimerState()
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

    private fun clearListeners() {
        listenerRegistrations.forEach { runCatching { it.remove() } }
        listenerRegistrations.clear()
        friendDocListeners.values.forEach { runCatching { it.remove() } }
        friendDocListeners.clear()
        remoteUserDuels.clear()
        remoteCollectionDuels.clear()
        liveFriendProfiles.clear()
        myFriendIds = emptySet()
    }

    private fun attachFirestoreListener(uid: String) {
        clearListeners()
        if (uid.isEmpty()) {
            rebuildState(loadCachedChallenges(), "", _state.value.myLiveMinutesToday)
            return
        }
        runCatching {
            val db = Firebase.firestore
            val users = db.collection(USERS_COLLECTION)

            // 0. Listen to my own user profile to get my username, my friends list, and cloud-saved challenge history
            listenerRegistrations += users.document(uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val uname = snapshot.getString("username").orEmpty()
                    if (uname.isNotBlank()) myResolvedUsername = uname
                    @Suppress("UNCHECKED_CAST")
                    val friendIds = (snapshot.get("friends") as? List<*>)?.mapNotNull { it as? String }
                        ?.filter { it.isNotBlank() && it != uid }
                        .orEmpty()
                    myFriendIds = friendIds.toSet()
                    val remoteHistoryJson = snapshot.getString("challengeHistoryJson")
                    if (!remoteHistoryJson.isNullOrBlank()) {
                        runCatching {
                            val arr = JSONArray(remoteHistoryJson)
                            for (i in 0 until arr.length()) {
                                val ch = runCatching { challengeFromJson(arr.getJSONObject(i)) }.getOrNull()
                                if (ch != null && ch.status != FriendChallengeStatus.CANCELLED && ch.id !in abandonedIds) {
                                    remoteCollectionDuels[ch.id] = remoteCollectionDuels[ch.id]?.let { mergeChallenges(it, ch) } ?: ch
                                }
                            }
                        }
                    }
                    ensureFriendProfileListeners(uid, friendIds)
                    handleIncomingUserDuelSnapshot(snapshot)
                    onRemoteUpdated(uid)
                }

            // 1. Listen for user documents where I am the challenged opponent
            listenerRegistrations += users
                .whereEqualTo("duelOpponentUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.documents?.forEach { doc ->
                        doc.checkIncomingUserNudge(uid)
                        doc.checkIncomingUserChatMessage(uid)
                        handleIncomingUserDuelSnapshot(doc)
                    }
                    onRemoteUpdated(uid)
                }

            // 2. Listen for user documents where I am in studyGroupParticipantUids
            listenerRegistrations += users
                .whereArrayContains("studyGroupParticipantUids", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.documents?.forEach { doc ->
                        doc.checkIncomingUserNudge(uid)
                        doc.checkIncomingUserChatMessage(uid)
                        handleIncomingUserDuelSnapshot(doc)
                    }
                    onRemoteUpdated(uid)
                }

            // 3. Listen for user documents where I am the challenger
            listenerRegistrations += users
                .whereEqualTo("duelChallengerUid", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.documents?.forEach { doc ->
                        doc.checkIncomingUserNudge(uid)
                        doc.checkIncomingUserChatMessage(uid)
                        handleIncomingUserDuelSnapshot(doc)
                    }
                    onRemoteUpdated(uid)
                }

            // 4. Listen on dedicated friend_challenges collection
            listenerRegistrations += db.collection(COLLECTION)
                .whereArrayContains("participants", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                            val removedId = change.document.id
                            markAbandoned(removedId)
                            remoteCollectionDuels.remove(removedId)
                        }
                    }
                    snapshot?.documents?.forEach { doc ->
                        val ch = doc.toFriendChallenge()
                        if (ch != null) {
                            if (ch.status == FriendChallengeStatus.CANCELLED) {
                                markAbandoned(ch.id)
                                remoteCollectionDuels[ch.id] = ch
                            } else if (ch.id !in abandonedIds) {
                                remoteCollectionDuels[ch.id] = ch
                            }
                        }
                    }
                    onRemoteUpdated(uid)
                }
        }.onFailure {
            Log.w(TAG, "Failed to attach Firestore listener: ${it.message}")
        }
    }

    private fun handleIncomingUserDuelSnapshot(doc: DocumentSnapshot) {
        val docUid = doc.id
        val rawStatus = doc.getString("duelStatus")?.lowercase().orEmpty()
        val currentDuelId = doc.getString("duelId").orEmpty()

        // If this user is the creator of a challenge we previously knew about, and they cancelled or cleared it:
        val prev = remoteUserDuels[docUid]
        if (prev != null && prev.challengerUid == docUid) {
            if (rawStatus == "cancelled" || rawStatus == "deleted" || currentDuelId.isBlank() || currentDuelId != prev.id) {
                markAbandoned(prev.id)
                remoteCollectionDuels.remove(prev.id)
                remoteUserDuels.remove(docUid)
            }
        }
        // Also check local cache in case the creator deleted the challenge while we were offline or before prev was populated
        loadCachedChallenges()
            .filter { it.challengerUid == docUid && (it.status == FriendChallengeStatus.ACTIVE || it.status == FriendChallengeStatus.PENDING) }
            .forEach { cachedByCreator ->
                if (rawStatus == "cancelled" || rawStatus == "deleted" || currentDuelId.isBlank() || currentDuelId != cachedByCreator.id) {
                    markAbandoned(cachedByCreator.id)
                    remoteCollectionDuels.remove(cachedByCreator.id)
                }
            }

        val ch = doc.toUserDuelChallenge()
        if (ch != null) {
            if (ch.status == FriendChallengeStatus.CANCELLED) {
                markAbandoned(ch.id)
                remoteCollectionDuels.remove(ch.id)
                remoteUserDuels[docUid] = ch
            } else if (ch.id !in abandonedIds) {
                remoteUserDuels[docUid] = ch
            } else {
                remoteUserDuels.remove(docUid)
            }
        } else {
            remoteUserDuels.remove(docUid)
        }
    }

    private fun ensureFriendProfileListeners(myUid: String, friendIds: List<String>) {
        if (myUid.isEmpty()) return
        friendIds.filter { it.isNotBlank() && it != myUid }.forEach { friendUid ->
            if (!friendDocListeners.containsKey(friendUid)) {
                runCatching {
                    val reg = Firebase.firestore.collection(USERS_COLLECTION).document(friendUid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null || snapshot == null) return@addSnapshotListener
                            snapshot.checkIncomingUserNudge(myUid)
                            snapshot.checkIncomingUserChatMessage(myUid)
                            val todayKey = LocalDate.now().toString()
                            val friendDayKey = snapshot.getString("studyTodayDayKey").orEmpty()
                            val todayMins = if (friendDayKey == todayKey) {
                                (snapshot.getLong("studyTodayMinutes") ?: 0L).toInt()
                            } else 0
                            val timerSt = if (friendDayKey == todayKey) {
                                StudyMemberTimerState.from(snapshot.getString("studyTimerStatus"))
                            } else StudyMemberTimerState.IDLE
                            val name = snapshot.getString("username").orEmpty().ifBlank { "Friend" }
                            liveFriendProfiles[friendUid] = StudyFriendOption(
                                uid = friendUid,
                                username = name,
                                todayMinutes = todayMins,
                                timerState = timerSt
                            )
                            handleIncomingUserDuelSnapshot(snapshot)
                            onRemoteUpdated(myUid)
                        }
                    friendDocListeners[friendUid] = reg
                }
            }
        }
    }

    private fun ensureFriendDocListeners(uid: String, challenges: List<FriendChallenge>) {
        if (uid.isEmpty()) return
        val activeOrPendingFriends = challenges
            .filter { it.status == FriendChallengeStatus.PENDING || it.status == FriendChallengeStatus.ACTIVE }
            .flatMap { it.participants }
            .filter { it.isNotEmpty() && it != uid }
            .distinct()

        ensureFriendProfileListeners(uid, activeOrPendingFriends)
    }

    @Synchronized
    private fun onRemoteUpdated(uid: String) {
        val byId = LinkedHashMap<String, FriendChallenge>()
        loadCachedChallenges().forEach { byId[it.id] = it }
        remoteCollectionDuels.values.toList().forEach { ch ->
            byId[ch.id] = byId[ch.id]?.let { mergeChallenges(it, ch) } ?: ch
        }
        remoteUserDuels.values.toList().forEach { ch ->
            byId[ch.id] = byId[ch.id]?.let { mergeChallenges(it, ch) } ?: ch
        }

        // Propagate any cancelled/deleted challenge to abandonedIds and clean up our own user doc if needed
        byId.values.filter { it.status == FriendChallengeStatus.CANCELLED }.forEach { cancelled ->
            markAbandoned(cancelled.id)
            remoteCollectionDuels.remove(cancelled.id)
            if (uid.isNotEmpty()) {
                val myRemote = remoteUserDuels[uid]
                if (myRemote != null && myRemote.id == cancelled.id && myRemote.status != FriendChallengeStatus.CANCELLED) {
                    remoteUserDuels.remove(uid)
                    appScope.launch {
                        runCatching {
                            Firebase.firestore.collection(USERS_COLLECTION)
                                .document(uid)
                                .set(
                                    mapOf(
                                        "duelId" to cancelled.id,
                                        "duelStatus" to "cancelled",
                                        "studyMessagesJson" to "[]",
                                        "studyNudgesJson" to "[]"
                                    ),
                                    SetOptions.merge()
                                )
                                .await()
                        }
                    }
                }
            }
        }

        // Overlay live friend profile minutes & timer states onto non-cancelled challenges
        val enrichedList = byId.values
            .filterNot { it.status == FriendChallengeStatus.CANCELLED }
            .map { ch -> enrichChallengeWithLiveProfiles(ch, uid) }

        // Check for new incoming invitations and nudges targeting me inside the challenges
        if (uid.isNotEmpty()) {
            val now = clock()
            enrichedList.forEach { ch ->
                if (ch.challengerUid != uid && now < ch.endAt &&
                    (ch.status == FriendChallengeStatus.PENDING || ch.status == FriendChallengeStatus.ACTIVE)
                ) {
                    val myMember = ch.allMembers().firstOrNull { it.uid == uid }
                    if (myMember != null &&
                        myMember.roleStatus == StudyMemberRoleStatus.INVITED &&
                        myMember.isInviteValid(now, ch.createdAt)
                    ) {
                        val inviteKey = "${ch.id}_${myMember.effectiveInvitedAt(ch.createdAt)}"
                        if (inviteKey !in handledInviteIds) {
                            markInviteHandled(inviteKey)
                            triggerIncomingInviteNotification(
                                fromFriendName = ch.challengerName.ifBlank { "Friend" },
                                targetMinutes = ch.targetMinutes
                            )
                        }
                    }
                }

                ch.nudges.forEach { nudge ->
                    if (nudge.toUid == uid && nudge.fromUid != uid &&
                        now - nudge.createdAt < NUDGE_FRESH_WINDOW_MS &&
                        nudge.id !in handledNudgeIds
                    ) {
                        markNudgeHandled(nudge.id)
                        triggerIncomingNudgeNotification(nudge.fromName.ifBlank { "Friend" })
                    }
                }

                // Trigger phone vibration + sound when a friend sends a chat message in our challenge
                if (now < ch.endAt && ch.id !in abandonedIds &&
                    (ch.status == FriendChallengeStatus.PENDING || ch.status == FriendChallengeStatus.ACTIVE) &&
                    (ch.hasActiveParticipant(uid) || uid in ch.participants)
                ) {
                    ch.messages.forEach { msg ->
                        if (msg.senderUid.isNotEmpty() &&
                            msg.senderUid != uid &&
                            !msg.isNudgeAlert &&
                            now - msg.createdAt < MESSAGE_FRESH_WINDOW_MS &&
                            msg.id !in handledMessageIds
                        ) {
                            markMessageHandled(msg.id)
                            triggerIncomingMessageNotification(
                                fromFriendName = msg.senderName.ifBlank { "Friend" },
                                messageText = msg.text
                            )
                        }
                    }
                }
            }
        }

        saveCachedChallenges(enrichedList)
        ensureFriendDocListeners(uid, enrichedList)
        rebuildState(enrichedList, uid, _state.value.myLiveMinutesToday)
    }

    private fun enrichChallengeWithLiveProfiles(ch: FriendChallenge, myUid: String): FriendChallenge {
        val isOngoing = ch.status == FriendChallengeStatus.ACTIVE || ch.status == FriendChallengeStatus.PENDING
        if (!isOngoing) {
            return ch
        }
        val currentMembers = ch.allMembers()
        val updatedMembers = currentMembers.map { m ->
            if (m.uid == myUid && myUid.isNotEmpty()) {
                val effectiveBaseline = maxOf(m.baselineMinutes, getPreChallengeMinutes(ch.createdAt))
                val myChallengeMins = (_state.value.myLiveMinutesToday - effectiveBaseline).coerceAtLeast(0)
                m.copy(
                    name = myResolvedUsername.ifBlank { m.name },
                    minutes = maxOf(m.minutes, myChallengeMins),
                    baselineMinutes = effectiveBaseline,
                    timerState = _state.value.myTimerState,
                    roleStatus = if (ch.id in abandonedIds) StudyMemberRoleStatus.SURRENDERED else m.roleStatus
                )
            } else {
                val live = liveFriendProfiles[m.uid]
                if (live != null) {
                    val friendChallengeMins = m.challengeMinutesFromToday(live.todayMinutes)
                    m.copy(
                        name = live.username.ifBlank { m.name },
                        minutes = maxOf(m.minutes, friendChallengeMins),
                        timerState = live.timerState
                    )
                } else m
            }
        }
        val challengerMins = updatedMembers.firstOrNull { it.uid == ch.challengerUid }?.minutes ?: ch.challengerMinutes
        val opponentMins = updatedMembers.firstOrNull { it.uid == ch.opponentUid }?.minutes ?: ch.opponentMinutes
        return ch.copy(
            challengerMinutes = maxOf(ch.challengerMinutes, challengerMins),
            opponentMinutes = maxOf(ch.opponentMinutes, opponentMins),
            members = updatedMembers
        )
    }

    /**
     * Tracks today's completed focus minutes + live running Pomodoro focus minutes + timer running/paused state,
     * and pushes updates to the active Study Group and user profile whenever they change.
     */
    private fun observeTodayFocusAndTimerState() {
        appScope.launch {
            val completedMinutesFlow = sessionDao.getAllSessions().map { all ->
                val today = LocalDate.now()
                val todaySessions = all.filter { it.completed && StatsCalculator.localDay(it.date) == today }
                todayCompletedSessions = todaySessions
                todaySessions.sumOf { it.durationSeconds } / 60
            }
            val liveTimerFlow = PomodoroTimerService.state.map { timer ->
                val liveMins = if (timer.phase == Phase.FOCUS && !timer.isUntouched) {
                    ((timer.phaseDurationMs - timer.remainingMs).coerceAtLeast(0L) / 60_000L).toInt()
                } else 0
                val status = when {
                    timer.isRunning && timer.phase == Phase.FOCUS -> StudyMemberTimerState.FOCUSING
                    (timer.phase == Phase.SHORT_BREAK || timer.phase == Phase.LONG_BREAK) &&
                        (timer.isRunning || !timer.isUntouched) -> StudyMemberTimerState.ON_BREAK
                    !timer.isRunning && !timer.isUntouched -> StudyMemberTimerState.PAUSED
                    else -> StudyMemberTimerState.IDLE
                }
                liveMins to status
            }.distinctUntilChanged()

            combine(completedMinutesFlow, liveTimerFlow) { completed, (live, timerStatus) ->
                (completed + live) to timerStatus
            }.distinctUntilChanged().collectLatest { (totalTodayMinutes, timerStatus) ->
                _state.value = _state.value.copy(
                    myLiveMinutesToday = totalTodayMinutes,
                    myTimerState = timerStatus
                )
                val uid = _state.value.myUid.ifEmpty { currentUid() }
                if (uid.isNotEmpty()) {
                    publishMyLiveStudyStatusToUserDoc(uid, totalTodayMinutes, timerStatus)
                }
                val active = _state.value.activeDuel ?: return@collectLatest
                if (uid.isNotEmpty()) {
                    val myMember = active.allMembers().firstOrNull { it.uid == uid }
                    val effectiveBaseline = maxOf(myMember?.baselineMinutes ?: 0, getPreChallengeMinutes(active.createdAt))
                    val myChallengeMinutes = (totalTodayMinutes - effectiveBaseline).coerceAtLeast(0)
                    if (active.myMinutes(uid) != myChallengeMinutes || myMember?.timerState != timerStatus) {
                        pushMyStateToActiveChallenge(active, uid, myChallengeMinutes, timerStatus)
                    }
                }
            }
        }
    }

    private fun publishMyLiveStudyStatusToUserDoc(uid: String, todayMinutes: Int, timerStatus: StudyMemberTimerState) {
        val todayKey = LocalDate.now().toString()
        appScope.launch {
            runCatching {
                Firebase.firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .set(
                        mapOf(
                            "studyTodayDayKey" to todayKey,
                            "studyTodayMinutes" to todayMinutes,
                            "studyTimerStatus" to timerStatus.name.lowercase(),
                            "studyUpdatedAt" to clock()
                        ),
                        SetOptions.merge()
                    )
                    .await()
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
                val uid = _state.value.myUid.ifEmpty { currentUid() }
                val hasExpiredIncoming = _state.value.incomingPending.any { !it.hasValidInvite(uid, now) }
                if (hasExpiredIncoming) {
                    rebuildState(loadCachedChallenges(), uid, _state.value.myLiveMinutesToday)
                }
                delay(2_000L)
            }
        }
    }

    private fun rebuildState(all: List<FriendChallenge>, uid: String, liveMinutes: Int) {
        val effectiveUid = uid.ifEmpty { _state.value.myUid }
        val now = clock()
        val myTimerSt = _state.value.myTimerState

        // Check if any active challenge has already passed midnight or reached its target hours
        all.filter { it.status == FriendChallengeStatus.ACTIVE }.forEach { ongoing ->
            val targetWinner = ongoing.findTargetReachedWinnerUid()
            if (targetWinner != null) {
                appScope.launch { completeTargetReachedChallenge(ongoing, targetWinner) }
            } else if (now >= ongoing.endAt) {
                appScope.launch { completeExpiredChallenge(ongoing) }
            }
        }

        // Challenges where I was invited within the last 30 minutes (including declined offers still within 30m)
        val incoming = all.filter { ch ->
            ch.id !in abandonedIds &&
                now < ch.endAt &&
                ch.challengerUid != effectiveUid &&
                (ch.status == FriendChallengeStatus.PENDING || ch.status == FriendChallengeStatus.ACTIVE) &&
                ch.hasValidInvite(effectiveUid, now)
        }.sortedWith(
            compareBy<FriendChallenge> { ch ->
                if (ch.isDeclinedOfferFor(effectiveUid, now)) 1 else 0
            }.thenByDescending { it.createdAt }
        )

        val outgoing = all.filter { ch ->
            ch.id !in abandonedIds &&
                ch.status == FriendChallengeStatus.PENDING &&
                ch.challengerUid == effectiveUid &&
                now < ch.endAt &&
                ch.allMembers().any { it.uid != effectiveUid && it.isInviteValid(now, ch.createdAt) }
        }.sortedByDescending { it.createdAt }

        // Active Study Group / Duel where I am an accepted (ACTIVE) participant
        val activeRaw = all.filter { ch ->
            ch.id !in abandonedIds &&
                !ch.didISurrender(effectiveUid) &&
                ch.status == FriendChallengeStatus.ACTIVE &&
                now < ch.endAt &&
                (effectiveUid.isEmpty() || ch.hasActiveParticipant(effectiveUid))
        }.maxByOrNull { it.acceptedAt ?: it.createdAt }

        // Overlay our live local minutes & timer status onto the active duel for instant UI responsiveness
        val active = activeRaw?.let { duel ->
            if (effectiveUid.isNotEmpty()) {
                val updatedMembers = duel.allMembers().map { m ->
                    if (m.uid == effectiveUid) {
                        val effectiveBaseline = maxOf(m.baselineMinutes, getPreChallengeMinutes(duel.createdAt))
                        val myChallengeMins = (liveMinutes - effectiveBaseline).coerceAtLeast(0)
                        m.copy(
                            minutes = maxOf(m.minutes, myChallengeMins),
                            baselineMinutes = effectiveBaseline,
                            timerState = myTimerSt
                        )
                    } else {
                        val liveFriend = liveFriendProfiles[m.uid]
                        if (liveFriend != null) {
                            val friendChallengeMins = m.challengeMinutesFromToday(liveFriend.todayMinutes)
                            m.copy(
                                name = liveFriend.username.ifBlank { m.name },
                                minutes = maxOf(m.minutes, friendChallengeMins),
                                timerState = liveFriend.timerState
                            )
                        } else m
                    }
                }
                val challengerMins = updatedMembers.firstOrNull { it.uid == duel.challengerUid }?.minutes ?: duel.challengerMinutes
                val opponentMins = updatedMembers.firstOrNull { it.uid == duel.opponentUid }?.minutes ?: duel.opponentMinutes
                duel.copy(
                    challengerMinutes = maxOf(duel.challengerMinutes, challengerMins),
                    opponentMinutes = maxOf(duel.opponentMinutes, opponentMins),
                    members = updatedMembers
                )
            } else duel
        }

        // If live minutes just reached the target hours for the active challenge, finish it immediately
        if (active != null) {
            val liveTargetWinner = active.findTargetReachedWinnerUid()
            if (liveTargetWinner != null) {
                appScope.launch { completeTargetReachedChallenge(active, liveTargetWinner) }
            }
        }

        // Auto-start the Pomodoro timer the first time a challenge becomes ACTIVE for this user
        if (active != null && active.id !in autoStartedIds && !active.isInvited(effectiveUid)) {
            markAutoStarted(active.id)
            startPomodoroForActiveChallenge()
            if (effectiveUid.isNotEmpty()) {
                val myChallengeMins = active.myMinutes(effectiveUid)
                appScope.launch { pushMyStateToActiveChallenge(active, effectiveUid, myChallengeMins, myTimerSt) }
            }
        }

        val history = all.filter {
            it.status != FriendChallengeStatus.CANCELLED &&
                (it.status == FriendChallengeStatus.COMPLETED ||
                    it.status == FriendChallengeStatus.SURRENDERED)
        }.sortedByDescending { it.finishedAt ?: it.createdAt }

        val uncelebrated = history.firstOrNull { ch ->
            effectiveUid.isNotEmpty() && ch.didIWin(effectiveUid) && ch.id !in celebratedIds
        }

        val friendsList = liveFriendProfiles.values
            .filter { it.uid in myFriendIds }
            .sortedBy { it.username.lowercase() }

        val winsCount = history.count { ch ->
            effectiveUid.isNotEmpty() && ch.didIWin(effectiveUid)
        }
        val prevWinsCount = _state.value.challengesWonCount
        val prevHistoryIds = _state.value.history.map { "${it.id}_${it.status}_${it.winnerUid}" }
        val newHistoryIds = history.map { "${it.id}_${it.status}_${it.winnerUid}" }

        _state.value = _state.value.copy(
            myUid = effectiveUid,
            myName = myResolvedUsername.ifBlank { _state.value.myName },
            myLiveMinutesToday = liveMinutes,
            myTimerState = myTimerSt,
            incomingPending = incoming.filter { it.id != active?.id },
            outgoingPending = outgoing,
            activeDuel = active,
            history = history,
            uncelebratedWin = uncelebrated,
            availableFriends = friendsList,
            challengesWonCount = winsCount
        )

        if (effectiveUid.isNotEmpty() && effectiveUid != "local_user" &&
            (winsCount != prevWinsCount || newHistoryIds != prevHistoryIds)
        ) {
            syncHistoryAndWinsToFirestoreAsync(effectiveUid, history, winsCount)
        }
    }

    private fun syncHistoryAndWinsToFirestoreAsync(
        uid: String,
        history: List<FriendChallenge>,
        winsCount: Int
    ) {
        if (uid.isEmpty() || uid == "local_user") return
        appScope.launch {
            val historyJson = runCatching {
                JSONArray(history.take(40).map { it.toJson() }).toString()
            }.getOrDefault("[]")
            runCatching {
                Firebase.firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .set(
                        mapOf(
                            "challengesWon" to winsCount,
                            "challengeHistoryJson" to historyJson
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
            runCatching {
                Firebase.firestore.collection("leaderboard")
                    .document(uid)
                    .set(
                        mapOf(
                            "uid" to uid,
                            "challengesWon" to winsCount
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
            // Also make sure each completed challenge is saved to the friend_challenges collection
            history.take(15).forEach { completed ->
                if (completed.challengerUid.isNotBlank() && completed.opponentUid.isNotBlank() &&
                    completed.challengerUid != completed.opponentUid
                ) {
                    runCatching {
                        Firebase.firestore.collection(COLLECTION)
                            .document(completed.id)
                            .set(completed.toFirestoreMap(), SetOptions.merge())
                            .await()
                    }
                }
            }
        }
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
        val snapshot = synchronized(autoStartedIds) {
            autoStartedIds.add(challengeId)
            autoStartedIds.toSet()
        }
        prefs.edit().putStringSet(KEY_AUTO_STARTED_IDS, snapshot).apply()
    }

    private fun markAbandoned(challengeId: String) {
        val snapshot = synchronized(abandonedIds) {
            abandonedIds.add(challengeId)
            abandonedIds.toSet()
        }
        prefs.edit().putStringSet(KEY_ABANDONED_IDS, snapshot).apply()
    }

    private fun markNudgeHandled(nudgeId: String) {
        val snapshot = synchronized(handledNudgeIds) {
            handledNudgeIds.add(nudgeId)
            handledNudgeIds.toSet()
        }
        prefs.edit().putStringSet(KEY_HANDLED_NUDGE_IDS, snapshot).apply()
    }

    private fun markInviteHandled(inviteKey: String) {
        val snapshot = synchronized(handledInviteIds) {
            handledInviteIds.add(inviteKey)
            handledInviteIds.toSet()
        }
        prefs.edit().putStringSet(KEY_HANDLED_INVITE_IDS, snapshot).apply()
    }

    private fun markMessageHandled(messageId: String) {
        val snapshot = synchronized(handledMessageIds) {
            handledMessageIds.add(messageId)
            handledMessageIds.toSet()
        }
        prefs.edit().putStringSet(KEY_HANDLED_MESSAGE_IDS, snapshot).apply()
    }

    fun acknowledgeCelebration(challengeId: String) {
        val snapshot = synchronized(celebratedIds) {
            celebratedIds.add(challengeId)
            celebratedIds.toSet()
        }
        prefs.edit().putStringSet(KEY_CELEBRATED_IDS, snapshot).apply()
        if (_state.value.uncelebratedWin?.id == challengeId) {
            _state.value = _state.value.copy(uncelebratedWin = null)
        }
    }

    fun dismissNudgeBanner() {
        _state.value = _state.value.copy(activeNudgeBanner = null)
        com.example.service.FloatingFocusBubbleManager.hideNudgeBanner(context)
    }

    /**
     * Publishes our Study Group / duel state to `/users/{myUid}` and `/friend_challenges/{id}`.
     */
    private fun syncChallengeToFirestoreAsync(challenge: FriendChallenge) {
        val myUid = currentUid()
        if (myUid.isEmpty()) return
        ensureFriendDocListeners(myUid, listOf(challenge))
        appScope.launch {
            runCatching {
                Firebase.firestore.collection(USERS_COLLECTION)
                    .document(myUid)
                    .set(challenge.toUserDuelFields(myUid), SetOptions.merge())
                    .await()
            }.onFailure {
                Log.w(TAG, "User duel sync warning: ${it.message}")
            }
            runCatching {
                Firebase.firestore.collection(COLLECTION)
                    .document(challenge.id)
                    .set(challenge.toFirestoreMap(), SetOptions.merge())
                    .await()
            }
        }
    }

    private suspend fun verifyConfirmedFriendIds(myUid: String, candidateUids: List<String>): Set<String> {
        if (myUid.isEmpty() || myUid == "local_user") return candidateUids.toSet()
        var currentFriends = myFriendIds
        if (candidateUids.any { it !in currentFriends }) {
            runCatching {
                val snap = Firebase.firestore.collection(USERS_COLLECTION).document(myUid).get().await()
                @Suppress("UNCHECKED_CAST")
                val fetched = (snap.get("friends") as? List<*>)?.mapNotNull { it as? String }
                    ?.filter { it.isNotBlank() && it != myUid }
                    .orEmpty()
                    .toSet()
                myFriendIds = fetched
                currentFriends = fetched
            }
        }
        return currentFriends
    }

    /**
     * Launches a new Study Group challenge with one or multiple friends, or invites those friends
     * to the currently active Study Group if one is already running.
     * Invited friends start with [StudyMemberRoleStatus.INVITED] and a 30-minute expiration window
     * (`invitedAt = now`). They only join the active leaderboard and chat room once they accept.
     */
    suspend fun startOrInviteToStudyGroup(
        challengerName: String,
        friendsToInvite: List<StudyFriendOption>,
        targetMinutes: Int = 0
    ): Result<FriendChallenge> = runCatching {
        require(friendsToInvite.isNotEmpty()) { "At least one friend is required" }
        val myUid = currentUid().ifEmpty { "local_user" }
        val confirmedFriends = verifyConfirmedFriendIds(myUid, friendsToInvite.map { it.uid })
        val allowedFriendsToInvite = friendsToInvite.filter { it.uid in confirmedFriends && it.uid != myUid }
        require(allowedFriendsToInvite.isNotEmpty()) { "You can only send challenge invitations to your friends" }
        val effectiveMyName = challengerName.ifBlank { myResolvedUsername.ifBlank { "You" } }
        val now = clock()
        val myMins = _state.value.myLiveMinutesToday
        val myTimerSt = _state.value.myTimerState
        val normalizedTargetMinutes = targetMinutes.coerceAtLeast(0)

        val existingActive = _state.value.activeDuel
        if (existingActive != null && existingActive.id !in abandonedIds && now < existingActive.endAt) {
            // Invite newly selected or expired/declined friends into the existing active Study Group
            val currentMembers = existingActive.allMembers().toMutableList()
            allowedFriendsToInvite.forEach { friend ->
                val idx = currentMembers.indexOfFirst { it.uid == friend.uid }
                val existingMember = if (idx >= 0) currentMembers[idx] else null
                val liveFriend = liveFriendProfiles[friend.uid]
                val roleStatus = if (existingMember?.roleStatus == StudyMemberRoleStatus.ACTIVE) {
                    StudyMemberRoleStatus.ACTIVE
                } else {
                    StudyMemberRoleStatus.INVITED
                }
                val friendTodayTotal = maxOf(friend.todayMinutes, liveFriend?.todayMinutes ?: 0).coerceAtLeast(0)
                val preservedBaseline = existingMember?.baselineMinutes?.takeIf { it > 0 } ?: friendTodayTotal
                val computedMinutes = if (existingMember != null) {
                    maxOf(existingMember.minutes, (friendTodayTotal - preservedBaseline).coerceAtLeast(0))
                } else {
                    0
                }
                val newMember = StudyGroupMember(
                    uid = friend.uid,
                    name = friend.username.ifBlank { liveFriend?.username ?: existingMember?.name ?: "Friend" },
                    minutes = computedMinutes,
                    timerState = liveFriend?.timerState ?: friend.timerState,
                    roleStatus = roleStatus,
                    invitedAt = if (roleStatus == StudyMemberRoleStatus.INVITED) now else existingMember?.invitedAt ?: now,
                    updatedAt = now,
                    baselineMinutes = preservedBaseline
                )
                if (idx >= 0) {
                    currentMembers[idx] = newMember
                } else {
                    currentMembers.add(newMember)
                }
            }
            val updated = existingActive.copy(
                status = FriendChallengeStatus.ACTIVE,
                members = currentMembers
            )
            upsertLocalCache(updated)
            syncChallengeToFirestoreAsync(updated)
            return@runCatching updated
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayKey = today.toString()
        val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Unique ID using current timestamp so abandoning a challenge never blocks creating a new one!
        val id = "${dayKey}_${myUid}_${now}"
        val primaryOpponent = allowedFriendsToInvite.first()

        val initialMembers = buildList {
            add(
                StudyGroupMember(
                    uid = myUid,
                    name = effectiveMyName,
                    minutes = 0,
                    timerState = myTimerSt,
                    roleStatus = StudyMemberRoleStatus.ACTIVE,
                    invitedAt = now,
                    updatedAt = now,
                    baselineMinutes = myMins.coerceAtLeast(0)
                )
            )
            allowedFriendsToInvite.distinctBy { it.uid }.filter { it.uid != myUid }.forEach { f ->
                val liveFriend = liveFriendProfiles[f.uid]
                val friendBaseline = maxOf(f.todayMinutes, liveFriend?.todayMinutes ?: 0).coerceAtLeast(0)
                add(
                    StudyGroupMember(
                        uid = f.uid,
                        name = f.username.ifBlank { liveFriend?.username ?: "Friend" },
                        minutes = 0,
                        timerState = liveFriend?.timerState ?: f.timerState,
                        roleStatus = StudyMemberRoleStatus.INVITED,
                        invitedAt = now,
                        updatedAt = now,
                        baselineMinutes = friendBaseline
                    )
                )
            }
        }

        val challenge = FriendChallenge(
            id = id,
            challengerUid = myUid,
            challengerName = effectiveMyName,
            challengerMinutes = 0,
            opponentUid = primaryOpponent.uid,
            opponentName = primaryOpponent.username.ifBlank { "Friend" },
            opponentMinutes = 0,
            dayKey = dayKey,
            createdAt = now,
            acceptedAt = now,
            endAt = midnight,
            finishedAt = null,
            status = FriendChallengeStatus.ACTIVE,
            members = initialMembers,
            messages = emptyList(),
            nudges = emptyList(),
            targetMinutes = normalizedTargetMinutes
        )

        upsertLocalCache(challenge)
        syncChallengeToFirestoreAsync(challenge)
        challenge
    }

    /**
     * Re-sends an expired or declined invitation for [friendUid] inside [challenge], resetting their 30-minute timer.
     */
    suspend fun resendInvite(
        challenge: FriendChallenge,
        friendUid: String,
        friendName: String
    ): Result<FriendChallenge> = runCatching {
        val myUid = currentUid().ifEmpty { "local_user" }
        val confirmedFriends = verifyConfirmedFriendIds(myUid, listOf(friendUid))
        require(friendUid in confirmedFriends) { "You can only send challenge invitations to your friends" }
        val now = clock()
        val liveFriend = liveFriendProfiles[friendUid]
        val currentMembers = challenge.allMembers().toMutableList()
        val idx = currentMembers.indexOfFirst { it.uid == friendUid }
        val existing = if (idx >= 0) currentMembers[idx] else null
        val friendTodayTotal = (liveFriend?.todayMinutes ?: 0).coerceAtLeast(0)
        val preservedBaseline = existing?.baselineMinutes?.takeIf { it > 0 } ?: friendTodayTotal
        val refreshedMember = StudyGroupMember(
            uid = friendUid,
            name = friendName.ifBlank { existing?.name ?: liveFriend?.username ?: "Friend" },
            minutes = maxOf(existing?.minutes ?: 0, (friendTodayTotal - preservedBaseline).coerceAtLeast(0)),
            timerState = liveFriend?.timerState ?: existing?.timerState ?: StudyMemberTimerState.IDLE,
            roleStatus = StudyMemberRoleStatus.INVITED,
            invitedAt = now,
            updatedAt = now,
            baselineMinutes = preservedBaseline
        )
        if (idx >= 0) {
            currentMembers[idx] = refreshedMember
        } else {
            currentMembers.add(refreshedMember)
        }
        val updated = challenge.copy(members = currentMembers)
        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)
        updated
    }

    /**
     * Sends a study challenge to [opponentUid] (or adds them to the current Study Group if active).
     */
    suspend fun sendChallenge(
        challengerName: String,
        opponentUid: String,
        opponentName: String,
        opponentInitialMinutes: Int = 0,
        targetMinutes: Int = 0
    ): Result<FriendChallenge> {
        val liveFriend = liveFriendProfiles[opponentUid]
        return startOrInviteToStudyGroup(
            challengerName = challengerName,
            friendsToInvite = listOf(
                StudyFriendOption(
                    uid = opponentUid,
                    username = opponentName.ifBlank { liveFriend?.username ?: "Friend" },
                    todayMinutes = maxOf(opponentInitialMinutes, liveFriend?.todayMinutes ?: 0),
                    timerState = liveFriend?.timerState ?: StudyMemberTimerState.IDLE
                )
            ),
            targetMinutes = targetMinutes
        )
    }

    /**
     * Accepts an incoming challenge or joins a Study Group room, transitions the member to ACTIVE, and starts the Pomodoro timer.
     */
    suspend fun acceptChallenge(challenge: FriendChallenge): Result<FriendChallenge> = runCatching {
        val myUid = currentUid().ifEmpty { _state.value.myUid.ifEmpty { challenge.opponentUid } }
        val now = clock()
        synchronized(abandonedIds) {
            if (abandonedIds.remove(challenge.id)) {
                prefs.edit().putStringSet(KEY_ABANDONED_IDS, abandonedIds.toSet()).apply()
            }
        }
        val myMins = _state.value.myLiveMinutesToday
        val myTimerSt = _state.value.myTimerState
        val updatedMembers = challenge.allMembers().map { m ->
            if (m.uid == myUid) {
                val effectiveBaseline = maxOf(m.baselineMinutes, getPreChallengeMinutes(challenge.createdAt))
                val myChallengeMins = (myMins - effectiveBaseline).coerceAtLeast(0)
                m.copy(
                    minutes = maxOf(m.minutes, myChallengeMins),
                    baselineMinutes = effectiveBaseline,
                    timerState = myTimerSt,
                    roleStatus = StudyMemberRoleStatus.ACTIVE,
                    updatedAt = now
                )
            } else m
        }
        val myAcceptedMins = updatedMembers.firstOrNull { it.uid == myUid }?.minutes ?: 0
        val updated = challenge.copy(
            status = FriendChallengeStatus.ACTIVE,
            acceptedAt = challenge.acceptedAt ?: now,
            opponentMinutes = if (challenge.opponentUid == myUid) maxOf(challenge.opponentMinutes, myAcceptedMins) else challenge.opponentMinutes,
            members = updatedMembers
        )
        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)
        updated
    }

    /**
     * Marks the user's invitation as DECLINED while keeping the invitation offered for the remaining
     * 30-minute window (`invitedAt + 30 min`) so the user can still accept before it expires.
     */
    suspend fun declineChallenge(challenge: FriendChallenge): Result<Unit> = runCatching {
        val myUid = currentUid().ifEmpty { _state.value.myUid.ifEmpty { challenge.opponentUid } }
        val now = clock()
        val updatedMembers = challenge.allMembers().map { m ->
            if (m.uid == myUid) {
                m.copy(
                    roleStatus = StudyMemberRoleStatus.DECLINED,
                    invitedAt = m.effectiveInvitedAt(challenge.createdAt),
                    updatedAt = now
                )
            } else m
        }
        val updated = challenge.copy(members = updatedMembers)
        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)
    }

    suspend fun cancelOutgoingChallenge(challenge: FriendChallenge): Result<Unit> =
        deleteStudyGroupByCreator(challenge)

    /**
     * Surrenders / abandons the active challenge so the user can immediately launch a new one!
     */
    suspend fun surrender(challenge: FriendChallenge): Result<FriendChallenge> = runCatching {
        val myUid = _state.value.myUid.ifEmpty { currentUid().ifEmpty { challenge.challengerUid } }
        val now = clock()
        markAbandoned(challenge.id)

        val updatedMembers = challenge.allMembers().map { m ->
            if (m.uid == myUid) m.copy(roleStatus = StudyMemberRoleStatus.SURRENDERED, updatedAt = now) else m
        }
        val remainingActive = updatedMembers.filter { it.roleStatus != StudyMemberRoleStatus.SURRENDERED }
        val winner = remainingActive.maxByOrNull { it.minutes }?.uid ?: challenge.friendUid(myUid)

        val isChallengeEnded = remainingActive.size <= 1 || challenge.isChallenger(myUid)
        val updated = challenge.copy(
            status = if (isChallengeEnded) FriendChallengeStatus.SURRENDERED else FriendChallengeStatus.ACTIVE,
            surrenderedByUid = myUid,
            winnerUid = if (isChallengeEnded) winner else challenge.winnerUid,
            finishedAt = if (isChallengeEnded) now else challenge.finishedAt,
            members = updatedMembers,
            messages = if (isChallengeEnded) emptyList() else challenge.messages,
            nudges = if (isChallengeEnded) emptyList() else challenge.nudges,
            messagesClearedAt = if (isChallengeEnded) maxOf(challenge.messagesClearedAt, now) else challenge.messagesClearedAt
        )

        // Remove from remote active maps for this user so activeDuel clears immediately
        remoteUserDuels.remove(myUid)
        remoteCollectionDuels.remove(challenge.id)

        val current = loadCachedChallenges().map { if (it.id == challenge.id) updated else it }
        saveCachedChallenges(current)
        rebuildState(current, myUid, _state.value.myLiveMinutesToday)

        if (myUid.isNotEmpty()) {
            appScope.launch {
                runCatching {
                    Firebase.firestore.collection(USERS_COLLECTION)
                        .document(myUid)
                        .set(
                            mapOf(
                                "duelId" to updated.id,
                                "duelStatus" to "surrendered",
                                "duelSurrenderedByUid" to myUid,
                                "duelWinnerUid" to winner,
                                "duelFinishedAt" to now,
                                "studyGroupParticipantUids" to emptyList<String>(),
                                "studyMembersJson" to membersToJsonArray(updatedMembers).toString(),
                                "studyMessagesJson" to if (isChallengeEnded) "[]" else messagesToJsonArray(updated.messages).toString(),
                                "studyNudgesJson" to if (isChallengeEnded) "[]" else nudgesToJsonArray(updated.nudges).toString(),
                                "studyMessagesClearedAt" to updated.messagesClearedAt
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }
                runCatching {
                    Firebase.firestore.collection(COLLECTION)
                        .document(updated.id)
                        .set(updated.toFirestoreMap(), SetOptions.merge())
                        .await()
                }
            }
        }
        updated
    }

    /**
     * Allows the creator of the Study Group challenge to delete all group chat messages at any time.
     */
    suspend fun clearStudyGroupMessagesByCreator(challenge: FriendChallenge): Result<FriendChallenge> = runCatching {
        val myUid = _state.value.myUid.ifEmpty { currentUid().ifEmpty { challenge.challengerUid } }
        require(myUid.isEmpty() || challenge.isChallenger(myUid) || challenge.challengerUid == "local_user") {
            "Only the challenge creator can clear all messages"
        }
        val now = clock()
        val updated = challenge.copy(
            messages = emptyList(),
            nudges = emptyList(),
            messagesClearedAt = now
        )
        remoteCollectionDuels[updated.id] = updated
        if (myUid.isNotEmpty()) {
            remoteUserDuels[myUid] = updated
        }
        val current = loadCachedChallenges().map { if (it.id == challenge.id) updated else it }
        saveCachedChallenges(current)
        rebuildState(current, myUid, _state.value.myLiveMinutesToday)
        syncChallengeToFirestoreAsync(updated)
        updated
    }

    /**
     * Allows the creator of an active Study Group / Friend Challenge to add extra focus minutes
     * to the challenge's target goal (`targetMinutes`), optionally posting an announcement message
     * in the group chat so all participants are notified in real time.
     */
    suspend fun addTargetMinutesByCreator(
        challenge: FriendChallenge,
        additionalMinutes: Int,
        announcementText: String = ""
    ): Result<FriendChallenge> = runCatching {
        val myUid = _state.value.myUid.ifEmpty { currentUid().ifEmpty { challenge.challengerUid } }
        require(myUid.isEmpty() || challenge.isChallenger(myUid) || challenge.challengerUid == "local_user") {
            "Only the challenge creator can add more time to the challenge"
        }
        require(additionalMinutes > 0) { "Additional minutes must be positive" }

        val maxMinutes = 24 * 60
        // Ensure if a participant already studied more than the old target (e.g., when converting an Open challenge),
        // adding time extends beyond the current base so the challenge continues cleanly.
        val baseTarget = if (challenge.targetMinutes > 0) {
            challenge.targetMinutes
        } else {
            val highestActiveMinutes = challenge.acceptedMembers().maxOfOrNull { it.minutes } ?: 0
            if (additionalMinutes <= highestActiveMinutes) highestActiveMinutes else 0
        }
        val newTargetMinutes = (baseTarget + additionalMinutes).coerceIn(1, maxMinutes)

        val now = clock()
        val myName = myResolvedUsername.ifBlank { challenge.myName(myUid).ifBlank { "You" } }
        val trimNotice = announcementText.trim()
        val updatedMessages = if (trimNotice.isNotEmpty()) {
            val msg = StudyGroupMessage(
                id = "${myUid}_${now}",
                senderUid = myUid,
                senderName = myName,
                text = trimNotice,
                createdAt = now,
                isNudgeAlert = false
            )
            markMessageHandled(msg.id)
            (challenge.messages + msg)
                .distinctBy { it.id }
                .sortedBy { it.createdAt }
                .takeLast(MAX_MESSAGES)
        } else {
            challenge.messages
        }

        val updated = challenge.copy(
            targetMinutes = newTargetMinutes,
            messages = updatedMessages
        )
        remoteCollectionDuels[updated.id] = updated
        if (myUid.isNotEmpty()) {
            remoteUserDuels[myUid] = updated
        }
        val cached = loadCachedChallenges()
        val nextCached = if (cached.any { it.id == challenge.id }) {
            cached.map { if (it.id == challenge.id) updated else it }
        } else {
            cached + updated
        }
        saveCachedChallenges(nextCached)
        rebuildState(nextCached, myUid, _state.value.myLiveMinutesToday)
        syncChallengeToFirestoreAsync(updated)
        updated
    }

    /**
     * Allows the creator of the Study Group challenge to delete the challenge and wipe all its messages.
     * Broadcasts a CANCELLED tombstone with participants intact so every invited friend's phone
     * immediately receives the update and closes the group in real time.
     */
    suspend fun deleteStudyGroupByCreator(challenge: FriendChallenge): Result<Unit> = runCatching {
        val myUid = _state.value.myUid.ifEmpty { currentUid().ifEmpty { challenge.challengerUid } }
        val now = clock()
        markAbandoned(challenge.id)
        remoteUserDuels.remove(myUid)
        remoteCollectionDuels.remove(challenge.id)

        val cancelledTombstone = challenge.copy(
            status = FriendChallengeStatus.CANCELLED,
            finishedAt = now,
            messages = emptyList(),
            nudges = emptyList(),
            messagesClearedAt = now
        )

        val current = loadCachedChallenges().filterNot { it.id == challenge.id }
        saveCachedChallenges(current)
        rebuildState(current, myUid, _state.value.myLiveMinutesToday)

        if (myUid.isNotEmpty()) {
            appScope.launch {
                runCatching {
                    Firebase.firestore.collection(USERS_COLLECTION)
                        .document(myUid)
                        .set(
                            cancelledTombstone.toUserDuelFields(myUid),
                            SetOptions.merge()
                        )
                        .await()
                }
                runCatching {
                    Firebase.firestore.collection(COLLECTION)
                        .document(challenge.id)
                        .set(
                            cancelledTombstone.toFirestoreMap(),
                            SetOptions.merge()
                        )
                        .await()
                }
            }
        }
    }

    /**
     * Sends a chat message or quick emoji inside the active Study Group challenge.
     */
    suspend fun sendStudyGroupMessage(challenge: FriendChallenge, rawText: String): Result<FriendChallenge> = runCatching {
        val text = rawText.trim()
        require(text.isNotEmpty()) { "Message cannot be empty" }
        val myUid = _state.value.myUid.ifEmpty { currentUid().ifEmpty { "local_user" } }
        val myName = myResolvedUsername.ifBlank { challenge.myName(myUid).ifBlank { "You" } }
        val now = clock()
        val msg = StudyGroupMessage(
            id = "${myUid}_${now}",
            senderUid = myUid,
            senderName = myName,
            text = text,
            createdAt = now,
            isNudgeAlert = false
        )
        markMessageHandled(msg.id)
        val mergedMessages = (challenge.messages + msg)
            .distinctBy { it.id }
            .sortedBy { it.createdAt }
            .takeLast(MAX_MESSAGES)

        val updated = challenge.copy(messages = mergedMessages)
        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)

        if (myUid.isNotEmpty() && myUid != "local_user") {
            appScope.launch {
                runCatching {
                    Firebase.firestore.collection(USERS_COLLECTION)
                        .document(myUid)
                        .set(
                            mapOf(
                                "lastChatMessageId" to msg.id,
                                "lastChatChallengeId" to challenge.id,
                                "lastChatSenderUid" to myUid,
                                "lastChatSenderName" to myName,
                                "lastChatText" to msg.text,
                                "lastChatAt" to now
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }
            }
        }
        updated
    }

    /**
     * Sends a Focus Nudge (sound + vibration alert) to a Study Group friend whose progress is paused/idle.
     */
    suspend fun sendFocusNudge(
        challenge: FriendChallenge,
        targetUid: String,
        targetName: String
    ): Result<FriendChallenge> = runCatching {
        val now = clock()
        val cooldownEnd = _state.value.nudgeCooldownUntil[targetUid] ?: 0L
        if (now < cooldownEnd) return@runCatching challenge

        val myUid = _state.value.myUid.ifEmpty { currentUid().ifEmpty { "local_user" } }
        val myName = myResolvedUsername.ifBlank { challenge.myName(myUid).ifBlank { "Friend" } }
        val nudgeId = "nudge_${myUid}_${targetUid}_${now}"
        val nudge = StudyGroupNudge(
            id = nudgeId,
            challengeId = challenge.id,
            fromUid = myUid,
            fromName = myName,
            toUid = targetUid,
            toName = targetName,
            createdAt = now
        )
        val alertMessage = StudyGroupMessage(
            id = "msg_$nudgeId",
            senderUid = myUid,
            senderName = myName,
            text = context.getString(R.string.study_group_nudge_chat_event, myName, targetName),
            createdAt = now,
            isNudgeAlert = true
        )

        _state.value = _state.value.copy(
            nudgeCooldownUntil = _state.value.nudgeCooldownUntil + (targetUid to (now + NUDGE_COOLDOWN_MS))
        )

        val updatedNudges = (challenge.nudges + nudge).distinctBy { it.id }.takeLast(20)
        val updatedMessages = (challenge.messages + alertMessage).distinctBy { it.id }.sortedBy { it.createdAt }.takeLast(MAX_MESSAGES)
        val updated = challenge.copy(nudges = updatedNudges, messages = updatedMessages)

        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)

        // Also publish nudge fields on my /users/{myUid} document so the friend's listener receives it immediately
        if (myUid.isNotEmpty()) {
            appScope.launch {
                runCatching {
                    Firebase.firestore.collection(USERS_COLLECTION)
                        .document(myUid)
                        .set(
                            mapOf(
                                "lastNudgeId" to nudge.id,
                                "lastNudgeChallengeId" to challenge.id,
                                "lastNudgeFromUid" to myUid,
                                "lastNudgeFromName" to myName,
                                "lastNudgeToUid" to targetUid,
                                "lastNudgeToName" to targetName,
                                "lastNudgeAt" to now
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }
            }
        }
        updated
    }

    private fun DocumentSnapshot.checkIncomingUserNudge(myUid: String) {
        if (myUid.isEmpty()) return
        val nudgeId = getString("lastNudgeId")?.takeIf { it.isNotBlank() } ?: return
        val toUid = getString("lastNudgeToUid").orEmpty()
        val fromUid = getString("lastNudgeFromUid").orEmpty()
        val fromName = getString("lastNudgeFromName").orEmpty().ifBlank { getString("username").orEmpty().ifBlank { "Friend" } }
        val nudgeAt = getLong("lastNudgeAt") ?: 0L
        val now = clock()
        if (toUid == myUid && fromUid != myUid && now - nudgeAt < NUDGE_FRESH_WINDOW_MS && nudgeId !in handledNudgeIds) {
            markNudgeHandled(nudgeId)
            triggerIncomingNudgeNotification(fromName)
        }
    }

    private fun DocumentSnapshot.checkIncomingUserChatMessage(myUid: String) {
        if (myUid.isEmpty()) return
        val msgId = getString("lastChatMessageId")?.takeIf { it.isNotBlank() } ?: return
        if (msgId in handledMessageIds) return
        val senderUid = getString("lastChatSenderUid").orEmpty()
        if (senderUid.isEmpty() || senderUid == myUid) return
        val chatAt = getLong("lastChatAt") ?: 0L
        val now = clock()
        if (now - chatAt >= MESSAGE_FRESH_WINDOW_MS) return

        val challengeId = getString("lastChatChallengeId").orEmpty()
        if (challengeId.isNotEmpty() && challengeId in abandonedIds) return
        val rawStatus = getString("duelStatus")?.lowercase().orEmpty()
        if (rawStatus == "cancelled" || rawStatus == "deleted" || rawStatus == "surrendered" || rawStatus == "completed") return

        @Suppress("UNCHECKED_CAST")
        val participantUids = (get("studyGroupParticipantUids") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val isMyChallenge = getString("duelOpponentUid") == myUid ||
            getString("duelChallengerUid") == myUid ||
            myUid in participantUids ||
            _state.value.activeDuel?.id == challengeId
        if (!isMyChallenge) return

        val senderName = getString("lastChatSenderName").orEmpty()
            .ifBlank { getString("username").orEmpty().ifBlank { "Friend" } }
        val text = getString("lastChatText").orEmpty()
        if (text.isBlank()) return

        markMessageHandled(msgId)
        triggerIncomingMessageNotification(senderName, text)
    }

    /**
     * Vibrates the phone and plays the notification chime (with a bundled audio fallback
     * so sound always plays reliably even on emulators without a system ringtone).
     */
    private fun vibrateAndPlayAlertSound(waveformTimings: LongArray) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attrs = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                        .build()
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(VibrationEffect.createWaveform(waveformTimings, -1), attrs)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(waveformTimings, -1)
                }
            }
        }

        runCatching {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val ringtone = if (soundUri != null) RingtoneManager.getRingtone(context, soundUri) else null
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                }
                ringtone.play()
            } else {
                playSuccessSound(context, R.raw.success_lagoon)
            }
        }.onFailure {
            runCatching { playSuccessSound(context, R.raw.success_lagoon) }
        }
    }

    /**
     * Plays notification sound + vibrates the device + displays a high-priority notification
     * when a Study Group friend sends a chat message or emoji in the challenge.
     */
    internal fun triggerIncomingMessageNotification(fromFriendName: String, messageText: String) {
        runCatching {
            val pattern = longArrayOf(0, 180, 80, 220)
            vibrateAndPlayAlertSound(pattern)

            createMessageNotificationChannel()
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("focusisland://app/challenges")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                MESSAGE_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val title = context.getString(R.string.study_group_message_notif_title, fromFriendName)

            val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_flame)
                .setContentTitle(title)
                .setContentText(messageText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSound(soundUri)
                .setVibrate(pattern)
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .build()

            context.getSystemService(NotificationManager::class.java)
                ?.notify(MESSAGE_NOTIFICATION_ID, notification)
        }.onFailure {
            Log.w(TAG, "Failed to trigger incoming message alert: ${it.message}")
        }
    }

    private fun createMessageNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val channel = NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    context.getString(R.string.study_group_message_notif_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.study_group_message_notif_channel_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 180, 80, 220)
                    setSound(soundUri, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Plays notification sound + vibrates the device + displays a high-priority notification
     * when a Study Group friend nudges the user to start/resume focusing.
     */
    internal fun triggerIncomingNudgeNotification(fromFriendName: String) {
        val now = clock()
        val bannerNudge = StudyGroupNudge(
            id = "nudge_${now}",
            challengeId = _state.value.activeDuel?.id.orEmpty(),
            fromUid = "",
            fromName = fromFriendName,
            toUid = _state.value.myUid,
            toName = _state.value.myName,
            createdAt = now
        )
        _state.value = _state.value.copy(activeNudgeBanner = bannerNudge)
        val inForeground = runCatching { com.example.FocusFlowApplication.instance.isAppInForeground }.getOrDefault(false)
        if (!inForeground) {
            com.example.service.FloatingFocusBubbleManager.showNudgeBanner(
                context = context,
                fromFriendName = fromFriendName,
                onReturnClicked = {
                    if (_state.value.activeNudgeBanner?.id == bannerNudge.id) {
                        _state.value = _state.value.copy(activeNudgeBanner = null)
                    }
                }
            )
        }

        runCatching {
            // 1. Vibrate device and play notification sound immediately
            vibrateAndPlayAlertSound(longArrayOf(0, 280, 120, 380))
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // 2. Post high-priority Android Notification with tap action to open Focus tab & start timer
            createNudgeNotificationChannel()
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("focusisland://app/focus")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                NUDGE_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val startTimerIntent = Intent(context, PomodoroTimerService::class.java).apply {
                action = PomodoroTimerService.ACTION_START
            }
            val startTimerPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    NUDGE_NOTIFICATION_ID + 1,
                    startTimerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    context,
                    NUDGE_NOTIFICATION_ID + 1,
                    startTimerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val title = context.getString(R.string.study_group_nudge_notif_title, fromFriendName)
            val body = context.getString(R.string.study_group_nudge_notif_body)

            val notification = NotificationCompat.Builder(context, NUDGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_flame)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 280, 120, 380))
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .addAction(0, context.getString(R.string.focus_start), startTimerPending)
                .build()

            context.getSystemService(NotificationManager::class.java)
                ?.notify(NUDGE_NOTIFICATION_ID, notification)
        }.onFailure {
            Log.w(TAG, "Failed to trigger nudge alert: ${it.message}")
        }
    }

    private fun createNudgeNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val channel = NotificationChannel(
                    NUDGE_CHANNEL_ID,
                    context.getString(R.string.study_group_nudge_notif_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.study_group_nudge_notif_channel_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 280, 120, 380)
                    setSound(soundUri, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun formatTargetDurationForNotif(minutes: Int): String {
        val safe = minutes.coerceAtLeast(1)
        val h = safe / 60
        val m = safe % 60
        return when {
            h > 0 && m > 0 -> context.getString(R.string.duration_h_m, h, m)
            h > 0 -> context.getString(R.string.duration_h, h)
            else -> context.getString(R.string.duration_m, m)
        }
    }

    /**
     * Plays notification sound + vibrates the device + displays a high-priority notification
     * when a friend invites the user to a Study Group or Challenge.
     */
    internal fun triggerIncomingInviteNotification(fromFriendName: String, targetMinutes: Int = 0) {
        runCatching {
            vibrateAndPlayAlertSound(longArrayOf(0, 220, 100, 260))
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            createInviteNotificationChannel()
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("focusisland://app/challenges")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                INVITE_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = context.getString(R.string.study_group_invite_notif_title, fromFriendName)
            val body = if (targetMinutes > 0) {
                context.getString(R.string.study_group_invite_notif_body_target, formatTargetDurationForNotif(targetMinutes))
            } else {
                context.getString(R.string.study_group_invite_notif_body_open)
            }

            val notification = NotificationCompat.Builder(context, INVITE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_widget_flame)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 220, 100, 260))
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .build()

            context.getSystemService(NotificationManager::class.java)
                ?.notify(INVITE_NOTIFICATION_ID, notification)
        }.onFailure {
            Log.w(TAG, "Failed to trigger invite notification: ${it.message}")
        }
    }

    private fun createInviteNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val channel = NotificationChannel(
                    INVITE_CHANNEL_ID,
                    context.getString(R.string.study_group_invite_notif_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.study_group_invite_notif_channel_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 220, 100, 260)
                    setSound(soundUri, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private suspend fun pushMyStateToActiveChallenge(
        challenge: FriendChallenge,
        myUid: String,
        minutes: Int,
        timerState: StudyMemberTimerState
    ) {
        val now = clock()
        val updatedMembers = challenge.allMembers().map { m ->
            if (m.uid == myUid) {
                m.copy(
                    name = myResolvedUsername.ifBlank { m.name },
                    minutes = minutes,
                    timerState = timerState,
                    updatedAt = now
                )
            } else m
        }
        val baseUpdated = if (challenge.isChallenger(myUid)) {
            challenge.copy(challengerMinutes = minutes, members = updatedMembers)
        } else if (challenge.opponentUid == myUid) {
            challenge.copy(opponentMinutes = minutes, members = updatedMembers)
        } else {
            challenge.copy(members = updatedMembers)
        }
        val targetWinner = baseUpdated.findTargetReachedWinnerUid()
        val updated = if (targetWinner != null) {
            baseUpdated.copy(
                status = FriendChallengeStatus.COMPLETED,
                winnerUid = targetWinner,
                finishedAt = now,
                messages = emptyList(),
                nudges = emptyList(),
                messagesClearedAt = maxOf(baseUpdated.messagesClearedAt, now)
            )
        } else {
            baseUpdated
        }
        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)
    }

    private suspend fun completeTargetReachedChallenge(challenge: FriendChallenge, winnerUid: String) {
        if (challenge.status == FriendChallengeStatus.COMPLETED) return
        val now = clock()
        val updated = challenge.copy(
            status = FriendChallengeStatus.COMPLETED,
            winnerUid = winnerUid,
            finishedAt = now,
            messages = emptyList(),
            nudges = emptyList(),
            messagesClearedAt = maxOf(challenge.messagesClearedAt, now)
        )
        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)
    }

    private suspend fun completeExpiredChallenge(challenge: FriendChallenge) {
        val winner = if (challenge.isTargetHoursChallenge) {
            challenge.findTargetReachedWinnerUid() ?: "none"
        } else {
            val activeMembers = challenge.activeAndInvitedMembers()
            if (activeMembers.size >= 2) {
                val top = activeMembers.maxByOrNull { it.minutes }
                val tiedCount = activeMembers.count { it.minutes == (top?.minutes ?: 0) }
                if (top != null && tiedCount == 1) top.uid else "tie"
            } else {
                when {
                    challenge.challengerMinutes > challenge.opponentMinutes -> challenge.challengerUid
                    challenge.opponentMinutes > challenge.challengerMinutes -> challenge.opponentUid
                    else -> "tie"
                }
            }
        }
        val finishedAt = challenge.endAt
        val now = clock()
        val updated = challenge.copy(
            status = FriendChallengeStatus.COMPLETED,
            winnerUid = winner,
            finishedAt = finishedAt,
            messages = emptyList(),
            nudges = emptyList(),
            messagesClearedAt = maxOf(challenge.messagesClearedAt, finishedAt, now)
        )
        upsertLocalCache(updated)
        syncChallengeToFirestoreAsync(updated)
    }

    private fun upsertLocalCache(challenge: FriendChallenge) {
        val existing = loadCachedChallenges().toMutableList()
        val idx = existing.indexOfFirst { it.id == challenge.id }
        if (idx >= 0) existing[idx] = mergeChallenges(existing[idx], challenge) else existing.add(0, challenge)
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
        private const val USERS_COLLECTION = "users"
        private const val PREFS_NAME = "friend_challenges_prefs"
        private const val KEY_CACHE = "cached_challenges"
        private const val KEY_CELEBRATED_IDS = "celebrated_ids"
        private const val KEY_AUTO_STARTED_IDS = "auto_started_ids"
        private const val KEY_ABANDONED_IDS = "abandoned_ids"
        private const val KEY_HANDLED_NUDGE_IDS = "handled_nudge_ids"
        private const val KEY_HANDLED_INVITE_IDS = "handled_invite_ids"
        private const val KEY_HANDLED_MESSAGE_IDS = "handled_message_ids"
        private const val NUDGE_CHANNEL_ID = "study_group_nudge_channel"
        private const val NUDGE_NOTIFICATION_ID = 4409
        private const val INVITE_CHANNEL_ID = "study_group_invite_channel"
        private const val INVITE_NOTIFICATION_ID = 4412
        private const val MESSAGE_CHANNEL_ID = "study_group_message_channel"
        private const val MESSAGE_NOTIFICATION_ID = 4415
        private const val NUDGE_FRESH_WINDOW_MS = 5 * 60_000L
        private const val MESSAGE_FRESH_WINDOW_MS = 5 * 60_000L
        internal const val NUDGE_COOLDOWN_MS = 60_000L
        internal const val INVITE_EXPIRY_MS = 30 * 60_000L
        private const val MAX_CACHE = 100
        private const val MAX_MESSAGES = 60

        private fun statusRank(status: FriendChallengeStatus): Int = when (status) {
            FriendChallengeStatus.PENDING -> 1
            FriendChallengeStatus.ACTIVE -> 2
            FriendChallengeStatus.DECLINED -> 3
            FriendChallengeStatus.COMPLETED -> 4
            FriendChallengeStatus.SURRENDERED -> 5
            FriendChallengeStatus.CANCELLED -> 6
        }

        internal fun mergeChallenges(a: FriendChallenge, b: FriendChallenge): FriendChallenge {
            val primary = if (statusRank(b.status) >= statusRank(a.status)) b else a
            val mergedMembersByUid = LinkedHashMap<String, StudyGroupMember>()
            (a.allMembers() + b.allMembers()).forEach { m ->
                val prev = mergedMembersByUid[m.uid]
                if (prev == null) {
                    mergedMembersByUid[m.uid] = m
                } else {
                    val prevInvitedAt = prev.invitedAt
                    val mInvitedAt = m.invitedAt
                    val maxInvitedAt = maxOf(prevInvitedAt, mInvitedAt)
                    val newer = if (m.updatedAt >= prev.updatedAt) m else prev
                    val role = when {
                        mInvitedAt > prevInvitedAt && m.updatedAt >= prev.updatedAt -> m.roleStatus
                        prevInvitedAt > mInvitedAt && prev.updatedAt >= m.updatedAt -> prev.roleStatus
                        prev.roleStatus == StudyMemberRoleStatus.SURRENDERED || m.roleStatus == StudyMemberRoleStatus.SURRENDERED ->
                            StudyMemberRoleStatus.SURRENDERED
                        prev.roleStatus == StudyMemberRoleStatus.ACTIVE || m.roleStatus == StudyMemberRoleStatus.ACTIVE ->
                            StudyMemberRoleStatus.ACTIVE
                        else -> newer.roleStatus
                    }
                    mergedMembersByUid[m.uid] = newer.copy(
                        name = m.name.ifBlank { prev.name },
                        minutes = maxOf(prev.minutes, m.minutes),
                        roleStatus = role,
                        invitedAt = maxInvitedAt,
                        updatedAt = maxOf(prev.updatedAt, m.updatedAt),
                        baselineMinutes = maxOf(prev.baselineMinutes, m.baselineMinutes)
                    )
                }
            }
            val maxClearedAt = maxOf(a.messagesClearedAt, b.messagesClearedAt)
            val isOngoing = primary.status == FriendChallengeStatus.PENDING || primary.status == FriendChallengeStatus.ACTIVE
            val mergedMessages = if (!isOngoing) {
                emptyList()
            } else {
                (a.messages + b.messages)
                    .filter { it.createdAt > maxClearedAt }
                    .distinctBy { it.id }
                    .sortedBy { it.createdAt }
                    .takeLast(MAX_MESSAGES)
            }
            val mergedNudges = if (!isOngoing) {
                emptyList()
            } else {
                (a.nudges + b.nudges)
                    .filter { it.createdAt > maxClearedAt }
                    .distinctBy { it.id }
                    .sortedBy { it.createdAt }
                    .takeLast(20)
            }

            return primary.copy(
                challengerName = b.challengerName.ifBlank { a.challengerName },
                opponentName = b.opponentName.ifBlank { a.opponentName },
                challengerMinutes = maxOf(a.challengerMinutes, b.challengerMinutes),
                opponentMinutes = maxOf(a.opponentMinutes, b.opponentMinutes),
                acceptedAt = listOfNotNull(a.acceptedAt, b.acceptedAt).maxOrNull(),
                finishedAt = listOfNotNull(a.finishedAt, b.finishedAt).maxOrNull(),
                surrenderedByUid = b.surrenderedByUid ?: a.surrenderedByUid,
                winnerUid = b.winnerUid ?: a.winnerUid,
                members = mergedMembersByUid.values.toList(),
                messages = mergedMessages,
                nudges = mergedNudges,
                messagesClearedAt = maxClearedAt,
                targetMinutes = maxOf(a.targetMinutes, b.targetMinutes)
            )
        }

        private fun membersToJsonArray(members: List<StudyGroupMember>): JSONArray =
            JSONArray(
                members.map { m ->
                    JSONObject()
                        .put("uid", m.uid)
                        .put("name", m.name)
                        .put("minutes", m.minutes)
                        .put("timerState", m.timerState.name.lowercase())
                        .put("roleStatus", m.roleStatus.name.lowercase())
                        .put("invitedAt", m.invitedAt)
                        .put("updatedAt", m.updatedAt)
                        .put("baselineMinutes", m.baselineMinutes)
                }
            )

        private fun parseMembersJson(raw: String?): List<StudyGroupMember> = runCatching {
            if (raw.isNullOrBlank()) return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val uid = o.optString("uid")
                if (uid.isBlank()) null else StudyGroupMember(
                    uid = uid,
                    name = o.optString("name"),
                    minutes = o.optInt("minutes", 0),
                    timerState = StudyMemberTimerState.from(o.optString("timerState")),
                    roleStatus = StudyMemberRoleStatus.from(o.optString("roleStatus")),
                    invitedAt = o.optLong("invitedAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L),
                    baselineMinutes = o.optInt("baselineMinutes", 0)
                )
            }
        }.getOrDefault(emptyList())

        private fun messagesToJsonArray(messages: List<StudyGroupMessage>): JSONArray =
            JSONArray(
                messages.map { m ->
                    JSONObject()
                        .put("id", m.id)
                        .put("senderUid", m.senderUid)
                        .put("senderName", m.senderName)
                        .put("text", m.text)
                        .put("createdAt", m.createdAt)
                        .put("isNudgeAlert", m.isNudgeAlert)
                }
            )

        private fun parseMessagesJson(raw: String?): List<StudyGroupMessage> = runCatching {
            if (raw.isNullOrBlank()) return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val text = o.optString("text")
                if (id.isBlank() || text.isBlank()) null else StudyGroupMessage(
                    id = id,
                    senderUid = o.optString("senderUid"),
                    senderName = o.optString("senderName"),
                    text = text,
                    createdAt = o.optLong("createdAt", 0L),
                    isNudgeAlert = o.optBoolean("isNudgeAlert", false)
                )
            }
        }.getOrDefault(emptyList())

        private fun nudgesToJsonArray(nudges: List<StudyGroupNudge>): JSONArray =
            JSONArray(
                nudges.map { n ->
                    JSONObject()
                        .put("id", n.id)
                        .put("challengeId", n.challengeId)
                        .put("fromUid", n.fromUid)
                        .put("fromName", n.fromName)
                        .put("toUid", n.toUid)
                        .put("toName", n.toName)
                        .put("createdAt", n.createdAt)
                }
            )

        private fun parseNudgesJson(raw: String?): List<StudyGroupNudge> = runCatching {
            if (raw.isNullOrBlank()) return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                if (id.isBlank()) null else StudyGroupNudge(
                    id = id,
                    challengeId = o.optString("challengeId"),
                    fromUid = o.optString("fromUid"),
                    fromName = o.optString("fromName"),
                    toUid = o.optString("toUid"),
                    toName = o.optString("toName"),
                    createdAt = o.optLong("createdAt", 0L)
                )
            }
        }.getOrDefault(emptyList())

        private fun DocumentSnapshot.toUserDuelChallenge(): FriendChallenge? = runCatching {
            val duelId = getString("duelId")?.takeIf { it.isNotBlank() } ?: return null
            val rawStatus = getString("duelStatus")?.lowercase() ?: return null
            val parsedStatus = FriendChallengeStatus.from(rawStatus)
            val challengerUid = getString("duelChallengerUid").orEmpty()
            val opponentUid = getString("duelOpponentUid").orEmpty()
            if (parsedStatus != FriendChallengeStatus.CANCELLED && (challengerUid.isBlank() || opponentUid.isBlank())) {
                return null
            }
            FriendChallenge(
                id = duelId,
                challengerUid = challengerUid,
                challengerName = getString("duelChallengerName").orEmpty(),
                challengerMinutes = (getLong("duelChallengerMinutes") ?: 0L).toInt(),
                opponentUid = opponentUid,
                opponentName = getString("duelOpponentName").orEmpty(),
                opponentMinutes = (getLong("duelOpponentMinutes") ?: 0L).toInt(),
                dayKey = getString("duelDayKey").orEmpty(),
                createdAt = getLong("duelCreatedAt") ?: 0L,
                acceptedAt = getLong("duelAcceptedAt"),
                endAt = getLong("duelEndAt") ?: 0L,
                finishedAt = getLong("duelFinishedAt"),
                status = parsedStatus,
                surrenderedByUid = getString("duelSurrenderedByUid")?.takeIf { it.isNotBlank() },
                winnerUid = getString("duelWinnerUid")?.takeIf { it.isNotBlank() },
                members = parseMembersJson(getString("studyMembersJson")),
                messages = parseMessagesJson(getString("studyMessagesJson")),
                nudges = parseNudgesJson(getString("studyNudgesJson")),
                messagesClearedAt = getLong("studyMessagesClearedAt") ?: 0L,
                targetMinutes = (getLong("duelTargetMinutes") ?: 0L).toInt()
            )
        }.getOrNull()

        private fun FriendChallenge.toUserDuelFields(myUid: String): Map<String, Any?> = mapOf(
            "uid" to myUid,
            "duelId" to id,
            "duelChallengerUid" to challengerUid,
            "duelChallengerName" to challengerName,
            "duelChallengerMinutes" to challengerMinutes,
            "duelOpponentUid" to opponentUid,
            "duelOpponentName" to opponentName,
            "duelOpponentMinutes" to opponentMinutes,
            "duelDayKey" to dayKey,
            "duelCreatedAt" to createdAt,
            "duelAcceptedAt" to acceptedAt,
            "duelEndAt" to endAt,
            "duelFinishedAt" to finishedAt,
            "duelStatus" to status.name.lowercase(),
            "duelSurrenderedByUid" to surrenderedByUid,
            "duelWinnerUid" to winnerUid,
            "studyGroupParticipantUids" to participants,
            "studyMembersJson" to membersToJsonArray(allMembers()).toString(),
            "studyMessagesJson" to messagesToJsonArray(messages).toString(),
            "studyNudgesJson" to nudgesToJsonArray(nudges).toString(),
            "studyMessagesClearedAt" to messagesClearedAt,
            "duelTargetMinutes" to targetMinutes
        )

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
                winnerUid = getString("winnerUid"),
                members = parseMembersJson(getString("studyMembersJson")),
                messages = parseMessagesJson(getString("studyMessagesJson")),
                nudges = parseNudgesJson(getString("studyNudgesJson")),
                messagesClearedAt = getLong("studyMessagesClearedAt") ?: 0L,
                targetMinutes = (getLong("targetMinutes") ?: 0L).toInt()
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
            "winnerUid" to winnerUid,
            "studyMembersJson" to membersToJsonArray(allMembers()).toString(),
            "studyMessagesJson" to messagesToJsonArray(messages).toString(),
            "studyNudgesJson" to nudgesToJsonArray(nudges).toString(),
            "studyMessagesClearedAt" to messagesClearedAt,
            "targetMinutes" to targetMinutes
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
            .put("studyMembersJson", membersToJsonArray(allMembers()).toString())
            .put("studyMessagesJson", messagesToJsonArray(messages).toString())
            .put("studyNudgesJson", nudgesToJsonArray(nudges).toString())
            .put("studyMessagesClearedAt", messagesClearedAt)
            .put("targetMinutes", targetMinutes)
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
            winnerUid = o.optString("winnerUid").takeIf { it.isNotEmpty() },
            members = parseMembersJson(o.optString("studyMembersJson")),
            messages = parseMessagesJson(o.optString("studyMessagesJson")),
            nudges = parseNudgesJson(o.optString("studyNudgesJson")),
            messagesClearedAt = o.optLong("studyMessagesClearedAt", 0L),
            targetMinutes = o.optInt("targetMinutes", 0)
        )
    }
}
