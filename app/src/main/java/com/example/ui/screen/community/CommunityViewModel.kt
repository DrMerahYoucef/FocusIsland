package com.example.ui.screen.community

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusFlowApplication
import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.FriendChallengeState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val treeCount: Int = 0,
    val totalMinutes: Int = 0,
    val points: Int = 0,
    val currentStreak: Int = 0,
    val currentRadio: String = "",
    val friends: List<String> = emptyList()
)

data class LeaderboardEntry(
    val uid: String = "",
    val username: String = "",
    val treeCount: Int = 0,
    val totalMinutes: Int = 0,
    val points: Int = 0,
    val currentStreak: Int = 0
)

data class FriendRequest(
    val fromUid: String = "",
    val toUid: String = "",
    val fromName: String = "",
    val toName: String = "",
    val status: String = ""
)

/** Ranking metric = Firestore field. treeCount is the number of completed sessions. */
enum class LeaderboardSort(val field: String) {
    POINTS("points"), FOCUS_TIME("totalMinutes"), SESSIONS("treeCount"), STREAK("currentStreak");

    fun of(entry: LeaderboardEntry): Int = when (this) {
        POINTS -> entry.points
        FOCUS_TIME -> entry.totalMinutes
        SESSIONS -> entry.treeCount
        STREAK -> entry.currentStreak
    }
}

data class LeaderboardState(
    val entries: List<LeaderboardEntry> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false
)

private fun UserProfile.toEntry() = LeaderboardEntry(uid, username, treeCount, totalMinutes, points, currentStreak)

/**
 * The ranked top list plus [people] (me and my friends, always shown with their live profile),
 * one row per person, best first; ties by name.
 */
internal fun mergeRanking(ranked: List<LeaderboardEntry>, people: List<UserProfile>, sort: LeaderboardSort): List<LeaderboardEntry> {
    val byUid = LinkedHashMap<String, LeaderboardEntry>()
    ranked.forEach { byUid[it.uid] = it }
    people.forEach { byUid[it.uid] = it.toEntry() }
    return byUid.values.sortedWith(compareByDescending<LeaderboardEntry> { sort.of(it) }.thenBy { it.username.lowercase() })
}

/** One-shot feedback shown in a snackbar. */
sealed interface CommunityMessage {
    data class RequestSent(val name: String) : CommunityMessage
    data class NowFriends(val name: String) : CommunityMessage
    data class FriendRemoved(val name: String) : CommunityMessage
    data class ChallengeSent(val name: String) : CommunityMessage
    data object ChallengeAccepted : CommunityMessage
    data object RequestDeclined : CommunityMessage
    data object RequestCancelled : CommunityMessage
    data object Failed : CommunityMessage
}

/**
 * Friends, requests and the leaderboard. Created only for signed-in members (guests see a call
 * to action instead), so [currentUid] is fixed for the lifetime of this view model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore: FirebaseFirestore = Firebase.firestore
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val friendChallengeRepo = FocusFlowApplication.instance.friendChallengeRepository

    val currentUid: String = Firebase.auth.currentUser?.uid.orEmpty()
    private val users get() = firestore.collection("users")
    private val requests get() = firestore.collection("friend_requests")

    private val _messages = Channel<CommunityMessage>(Channel.BUFFERED)
    val messages: Flow<CommunityMessage> = _messages.receiveAsFlow()

    val friendChallengeState: StateFlow<FriendChallengeState> = friendChallengeRepo.state

    // ------------------------------------------------------------------ profile & friends

    val myProfile: StateFlow<UserProfile?> = documentFlow(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    /** Friend profiles, live; re-queried whenever my friends list changes. */
    val friends: StateFlow<List<UserProfile>> = myProfile
        .map { it?.friends.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { ids -> profilesFlow(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val pendingRequests: StateFlow<List<FriendRequest>> =
        requestsFlow(requests.whereEqualTo("toUid", currentUid).whereEqualTo("status", "pending"))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val sentRequests: StateFlow<List<FriendRequest>> =
        requestsFlow(requests.whereEqualTo("fromUid", currentUid).whereEqualTo("status", "pending"))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    // ------------------------------------------------------------------ leaderboard

    private val _sort = MutableStateFlow(LeaderboardSort.POINTS)
    val sort: StateFlow<LeaderboardSort> = _sort.asStateFlow()

    /**
     * Top 100 members by the chosen metric, plus me and my friends wherever they rank.
     * Read from the profiles (`users`), which every app version keeps up to date; the separate
     * `leaderboard` collection missed members whose app never wrote to it.
     */
    val leaderboard: StateFlow<LeaderboardState> = combine(
        _sort.flatMapLatest { rankingFlow(it) },
        myProfile,
        friends,
        _sort
    ) { ranked, me, friends, sort ->
        if (ranked.loading || ranked.failed) ranked
        else ranked.copy(entries = mergeRanking(ranked.entries, friends + listOfNotNull(me), sort))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), LeaderboardState())

    fun setSort(sort: LeaderboardSort) {
        _sort.value = sort
    }

    // ------------------------------------------------------------------ search

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /**
     * Prefix search on usernames. Firestore comparisons are case-sensitive, so the query is
     * tried as typed, in lower case and capitalised, and the results merged.
     */
    fun searchUsers(query: String) {
        val q = query.trim()
        if (q.length < MIN_SEARCH) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searching.value = true
            try {
                val variants = linkedSetOf(q, q.lowercase(), q.lowercase().replaceFirstChar { it.titlecase() })
                val found = LinkedHashMap<String, UserProfile>()
                for (v in variants) {
                    users.whereGreaterThanOrEqualTo("username", v)
                        .whereLessThanOrEqualTo("username", v + "\uf8ff")
                        .limit(10)
                        .get().await()
                        .documents.mapNotNull { it.toProfile() }
                        .forEach { found.putIfAbsent(it.uid, it) }
                }
                _searchResults.value = found.values.filter { it.uid != currentUid }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Search failed", e)
                _searchResults.value = emptyList()
                _messages.trySend(CommunityMessage.Failed)
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    // ------------------------------------------------------------------ actions

    fun sendFriendRequest(toUid: String, toName: String) {
        if (toUid.isEmpty() || toUid == currentUid) return
        launchAction(CommunityMessage.RequestSent(toName)) {
            val myName = myProfile.value?.username
                ?: users.document(currentUid).get().await().getString("username").orEmpty()
            requests.document("${currentUid}_$toUid").set(
                mapOf(
                    "fromUid" to currentUid,
                    "toUid" to toUid,
                    "fromName" to myName,
                    "toName" to toName,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    /** One atomic batch: request accepted + each of us in the other's friends list. */
    fun acceptRequest(request: FriendRequest) {
        if (request.fromUid.isEmpty()) return
        launchAction(CommunityMessage.NowFriends(request.fromName)) {
            firestore.batch()
                .update(requests.document("${request.fromUid}_$currentUid"), "status", "accepted")
                .update(users.document(currentUid), "friends", FieldValue.arrayUnion(request.fromUid))
                .update(users.document(request.fromUid), "friends", FieldValue.arrayUnion(currentUid))
                .commit().await()
        }
    }

    fun declineRequest(request: FriendRequest) {
        if (request.fromUid.isEmpty()) return
        launchAction(CommunityMessage.RequestDeclined) {
            requests.document("${request.fromUid}_$currentUid").update("status", "declined").await()
        }
    }

    fun cancelRequest(request: FriendRequest) {
        if (request.toUid.isEmpty()) return
        launchAction(CommunityMessage.RequestCancelled) {
            requests.document("${currentUid}_${request.toUid}").delete().await()
        }
    }

    fun removeFriend(friend: UserProfile) {
        if (friend.uid.isEmpty()) return
        launchAction(CommunityMessage.FriendRemoved(friend.username)) {
            firestore.batch()
                .update(users.document(currentUid), "friends", FieldValue.arrayRemove(friend.uid))
                .update(users.document(friend.uid), "friends", FieldValue.arrayRemove(currentUid))
                .commit().await()
        }
    }

    fun challengeFriend(opponentUid: String, opponentName: String) {
        if (opponentUid.isEmpty() || opponentUid == currentUid) return
        viewModelScope.launch {
            val myName = myProfile.value?.username?.takeIf { it.isNotBlank() }
                ?: runCatching { users.document(currentUid).get().await().getString("username").orEmpty() }.getOrDefault("")
            val result = friendChallengeRepo.sendChallenge(
                challengerName = myName.ifBlank { "You" },
                opponentUid = opponentUid,
                opponentName = opponentName
            )
            if (result.isSuccess) {
                _messages.send(CommunityMessage.ChallengeSent(opponentName))
            } else {
                _messages.send(CommunityMessage.Failed)
            }
        }
    }

    fun acceptFriendChallenge(challenge: FriendChallenge) {
        viewModelScope.launch {
            val res = friendChallengeRepo.acceptChallenge(challenge)
            if (res.isSuccess) {
                _messages.send(CommunityMessage.ChallengeAccepted)
            } else {
                _messages.send(CommunityMessage.Failed)
            }
        }
    }

    fun declineFriendChallenge(challenge: FriendChallenge) {
        viewModelScope.launch {
            friendChallengeRepo.declineChallenge(challenge)
        }
    }

    fun acknowledgeFriendWin(challengeId: String) {
        friendChallengeRepo.acknowledgeCelebration(challengeId)
    }

    // ------------------------------------------------------------------ listening status (opt-in)

    private val _shareListening = MutableStateFlow(prefs.getBoolean(KEY_SHARE_LISTENING, false))
    val shareListening: StateFlow<Boolean> = _shareListening.asStateFlow()

    // The same switch exists in Settings: stay in sync when it changes there.
    private val shareListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == KEY_SHARE_LISTENING) _shareListening.value = p.getBoolean(KEY_SHARE_LISTENING, false)
    }.also { prefs.registerOnSharedPreferenceChangeListener(it) }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(shareListener)
        super.onCleared()
    }

    /** Off by default. Turning it off also clears what friends currently see. */
    fun setShareListening(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHARE_LISTENING, enabled).apply()
        _shareListening.value = enabled
        if (!enabled) {
            viewModelScope.launch {
                runCatching { users.document(currentUid).update("currentRadio", "").await() }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun launchAction(success: CommunityMessage, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _messages.send(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Community action failed", e)
                _messages.send(CommunityMessage.Failed)
            }
        }
    }

    private fun documentFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Profile listener failed", error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { registration.remove() }
    }

    /** Firestore `in` accepts at most 30 ids, so large friend lists are split into several listeners. */
    private fun profilesFlow(ids: List<String>): Flow<List<UserProfile>> = callbackFlow {
        if (ids.isEmpty()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val chunks = ids.distinct().chunked(MAX_IN_QUERY)
        val results = arrayOfNulls<List<UserProfile>>(chunks.size)
        val registrations = chunks.mapIndexed { index, chunk ->
            users.whereIn(FieldPath.documentId(), chunk).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Friends listener failed", error)
                    return@addSnapshotListener
                }
                results[index] = snapshot?.documents?.mapNotNull { it.toProfile() }.orEmpty()
                if (results.all { it != null }) {
                    trySend(results.flatMap { it.orEmpty() }.sortedBy { it.username.lowercase() })
                }
            }
        }
        awaitClose { registrations.forEach { it.remove() } }
    }

    private fun requestsFlow(query: Query): Flow<List<FriendRequest>> = callbackFlow {
        if (currentUid.isEmpty()) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Requests listener failed", error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(FriendRequest::class.java) }.orEmpty())
        }
        awaitClose { registration.remove() }
    }

    private fun rankingFlow(sort: LeaderboardSort): Flow<LeaderboardState> = callbackFlow {
        trySend(LeaderboardState(loading = true))
        val registration = users
            .orderBy(sort.field, Query.Direction.DESCENDING)
            .limit(LEADERBOARD_SIZE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Leaderboard listener failed", error)
                    trySend(LeaderboardState(loading = false, failed = true))
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { it.toProfile()?.toEntry() }.orEmpty()
                trySend(LeaderboardState(entries = entries, loading = false))
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.int(field: String) = (getLong(field) ?: 0L).toInt()

    private fun DocumentSnapshot.toProfile(): UserProfile? = runCatching {
        UserProfile(
            uid = getString("uid") ?: id,
            username = getString("username").orEmpty(),
            treeCount = int("treeCount"),
            totalMinutes = int("totalMinutes"),
            points = int("points"),
            currentStreak = int("currentStreak"),
            currentRadio = getString("currentRadio").orEmpty(),
            friends = (get("friends") as? List<*>).orEmpty().mapNotNull { it?.toString() }
        )
    }.getOrNull()

    companion object {
        private const val TAG = "CommunityViewModel"
        private const val STOP_TIMEOUT = 5_000L
        private const val MAX_IN_QUERY = 30
        private const val LEADERBOARD_SIZE = 100L
        const val MIN_SEARCH = 2
        const val PREFS = "focusflow_prefs"

        /** Read by RadioPlayerService before publishing the station name. */
        const val KEY_SHARE_LISTENING = "share_listening"
    }
}
