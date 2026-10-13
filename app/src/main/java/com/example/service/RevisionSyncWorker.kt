package com.example.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.FocusFlowApplication
import com.example.data.db.entity.SyncStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Cloud backup of card TEXT and review history to users/{uid}/revision_notes.
 * Uploads the structured content (contentBlocksJson), not just the legacy markdown, and removes
 * cloud copies of deleted cards. Photos and recordings stay on the device (use Backup & restore for those).
 */
class RevisionSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = Firebase.auth.currentUser
        if (user == null || user.isAnonymous) return Result.success()
        val notesRef = Firebase.firestore.collection("users").document(user.uid).collection("revision_notes")
        val repository = FocusFlowApplication.instance.revisionRepository

        return try {
            inputData.getStringArray(KEY_DELETE_IDS).orEmpty().forEach { id ->
                notesRef.document(id).delete().await()
            }

            for (note in repository.getUnsyncedNotes()) {
                when (note.syncStatus) {
                    SyncStatus.PENDING_UPLOAD -> {
                        val docRef = note.firestoreId?.let(notesRef::document) ?: notesRef.document(note.id)
                        docRef.set(
                            mapOf(
                                "id" to note.id,
                                "deckId" to note.deckId,
                                "title" to note.title,
                                "contentBlocksJson" to note.contentBlocksJson,
                                "plainTextPreview" to note.plainTextPreview,
                                "contentMarkdown" to note.contentMarkdown,
                                "mediaType" to note.mediaType,
                                "createdAt" to note.createdAt,
                                "updatedAt" to note.updatedAt,
                                "easeFactor" to note.easeFactor,
                                "intervalDays" to note.intervalDays,
                                "repetitions" to note.repetitions,
                                "dueDate" to note.dueDate,
                                "lastReviewedAt" to note.lastReviewedAt
                            ),
                            SetOptions.merge()
                        ).await()
                        // Only mark synced if the card wasn't edited while uploading.
                        val current = repository.getNoteById(note.id)
                        if (current != null && current.updatedAt == note.updatedAt) {
                            repository.upsertNote(current.copy(firestoreId = docRef.id, syncStatus = SyncStatus.SYNCED))
                        }
                    }
                    SyncStatus.PENDING_DELETE -> {
                        note.firestoreId?.let { notesRef.document(it).delete().await() }
                        repository.deleteNote(note)
                    }
                    SyncStatus.SYNCED -> Unit
                }
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Card sync failed, will retry: ${e.message}")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "RevisionSyncWorker"
        private const val KEY_DELETE_IDS = "delete_ids"
        private const val MAX_ATTEMPTS = 5

        private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun scheduleSyncWork(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<RevisionSyncWorker>().setConstraints(networkConstraint).build()
            )
        }

        /** Deletes cloud copies of cards removed on this device (runs when online). */
        fun scheduleRemoteDelete(context: Context, firestoreIds: List<String>) {
            if (firestoreIds.isEmpty()) return
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<RevisionSyncWorker>()
                    .setConstraints(networkConstraint)
                    .setInputData(workDataOf(KEY_DELETE_IDS to firestoreIds.toTypedArray()))
                    .build()
            )
        }

        fun schedulePeriodicSyncWork(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "revision_periodic_sync",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RevisionSyncWorker>(6, TimeUnit.HOURS).setConstraints(networkConstraint).build()
            )
        }
    }
}
