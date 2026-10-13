package com.example.data.repository

import android.util.Log
import com.example.data.db.dao.SessionDao
import com.example.data.db.entity.SessionEntity
import com.example.data.stats.FocusTotals
import com.example.data.stats.StatsCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Focus sessions: Room is the source of truth, Firestore is a mirror.
 *
 * This is the ONLY place that writes focus statistics (treeCount, totalMinutes, points,
 * currentStreak) to Firestore. Statistics are always recomputed from real sessions;
 * nothing is ever synthesized.
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val appScope: CoroutineScope,
    private val onSessionsChanged: () -> Unit
) {
    private val syncMutex = Mutex()

    suspend fun insert(session: SessionEntity) {
        sessionDao.insert(session)
        onSessionsChanged()
        appScope.launch { syncWithFirestore() }
    }

    fun getSessionsInRange(from: Long, to: Long): Flow<List<SessionEntity>> = sessionDao.getSessionsInRange(from, to)
    fun getTotalFocusSeconds(from: Long, to: Long): Flow<Long?> = sessionDao.getTotalFocusSeconds(from, to)
    fun getSessionCount(from: Long, to: Long): Flow<Int> = sessionDao.getSessionCount(from, to)
    fun getTotalScore(from: Long, to: Long): Flow<Int?> = sessionDao.getTotalScore(from, to)
    fun getAllSessions(): Flow<List<SessionEntity>> = sessionDao.getAllSessions()

    suspend fun totals(): FocusTotals = StatsCalculator.totals(sessionDao.getAllSessionsList())

    suspend fun deleteAllLocal() {
        sessionDao.deleteAll()
        onSessionsChanged()
    }

    /**
     * Two-way merge keyed by session end time, then publishes the recomputed stats.
     * Safe to call from anywhere; concurrent calls are serialized. Never throws (except cancellation).
     */
    suspend fun syncWithFirestore() = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val user = Firebase.auth.currentUser ?: return@withContext
                if (user.isAnonymous) return@withContext // guests have no cloud profile
                val uid = user.uid
                val db = Firebase.firestore
                val sessionsRef = db.collection("users").document(uid).collection("sessions")

                val remote = sessionsRef.get().await().documents.mapNotNull { doc ->
                    val date = doc.getLong("date") ?: return@mapNotNull null
                    SessionEntity(
                        date = date,
                        durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 0,
                        completed = doc.getBoolean("completed") ?: false,
                        focusScore = doc.getLong("focusScore")?.toInt() ?: 0
                    )
                }.associateBy { it.date }

                val local = sessionDao.getAllSessionsList()
                val localDates = local.mapTo(HashSet()) { it.date }

                // Remote → local
                val missingLocally = remote.values.filter { it.date !in localDates }
                if (missingLocally.isNotEmpty()) sessionDao.insertAll(missingLocally)

                // Local → remote (Firestore batches are capped at 500 writes)
                local.filter { it.date !in remote }.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.set(sessionsRef.document(it.date.toString()), it.toFirestoreMap()) }
                    batch.commit().await()
                }

                // Publish stats computed from the now-merged local history.
                val totals = StatsCalculator.totals(sessionDao.getAllSessionsList())
                val stats = mapOf(
                    "uid" to uid,
                    "treeCount" to totals.completedSessions,
                    "totalMinutes" to totals.focusMinutes,
                    "points" to totals.points,
                    "currentStreak" to totals.currentStreakDays
                )
                // Profiles are readable by other members: remove the email older versions stored there.
                db.collection("users").document(uid).set(stats + ("email" to FieldValue.delete()), SetOptions.merge()).await()
                db.collection("leaderboard").document(uid).set(stats, SetOptions.merge()).await()

                if (missingLocally.isNotEmpty()) onSessionsChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Firestore session sync skipped: ${e.message}")
            }
        }
    }

    private fun SessionEntity.toFirestoreMap() = mapOf(
        "date" to date,
        "durationSeconds" to durationSeconds,
        "completed" to completed,
        "focusScore" to focusScore
    )

    private companion object {
        const val TAG = "SessionRepository"
    }
}
