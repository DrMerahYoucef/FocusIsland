package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "revision_notes",
    indices = [
        Index(value = ["deckId", "dueDate"]),
        Index(value = ["dueDate"])
    ]
)
data class RevisionNoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val deckId: String,
    val title: String,
    val contentBlocksJson: String = "",   // authoritative structured content — rendered directly by NoteBlocksRenderer
    val plainTextPreview: String = "",    // flattened text for search/export ONLY — never rendered
    val contentMarkdown: String = "",     // legacy column kept for backwards compatibility
    val mediaType: String? = null,        // "IMAGE", "AUDIO", or null for text/OCR cards
    val mediaFilePath: String? = null,    // absolute path inside the app's media folder
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // --- FSRS-5 scheduling fields ---
    val stability: Float = 0f,
    val difficulty: Float = 0f,
    val retrievability: Float = 1f,
    val state: String = "NEW",            // "NEW", "LEARNING", "REVIEW", "RELEARNING"
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val dueDate: Long = System.currentTimeMillis(), // next review date timestamp (ms)
    val lastReviewedAt: Long? = null,

    // --- sync metadata ---
    val firestoreId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD
)
