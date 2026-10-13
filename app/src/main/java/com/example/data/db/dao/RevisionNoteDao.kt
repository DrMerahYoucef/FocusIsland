package com.example.data.db.dao

import androidx.room.*
import com.example.data.db.entity.RevisionNoteEntity
import kotlinx.coroutines.flow.Flow

/**
 * "Now" is always passed in explicitly. A default parameter is evaluated once and
 * freezes the query at that moment, so cards becoming due later never appear.
 */
@Dao
interface RevisionNoteDao {
    @Query("SELECT * FROM revision_notes WHERE dueDate <= :now ORDER BY dueDate ASC")
    suspend fun getDueNotesOnce(now: Long): List<RevisionNoteEntity>

    @Query("SELECT * FROM revision_notes WHERE deckId = :deckId AND dueDate <= :now ORDER BY dueDate ASC")
    suspend fun getDueNotesForDeckOnce(deckId: String, now: Long): List<RevisionNoteEntity>

    @Query("SELECT * FROM revision_notes WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun getNotesForDeck(deckId: String): Flow<List<RevisionNoteEntity>>

    @Query("SELECT * FROM revision_notes WHERE deckId = :deckId ORDER BY dueDate ASC")
    suspend fun getNotesForDeckOnce(deckId: String): List<RevisionNoteEntity>

    @Query("SELECT * FROM revision_notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<RevisionNoteEntity>>

    @Query("SELECT * FROM revision_notes ORDER BY createdAt DESC")
    suspend fun getAllNotesOnce(): List<RevisionNoteEntity>

    @Query("SELECT * FROM revision_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RevisionNoteEntity?

    @Query("SELECT COUNT(*) FROM revision_notes WHERE dueDate <= :now")
    suspend fun getDueCount(now: Long): Int

    @Query("SELECT COUNT(*) FROM revision_notes")
    suspend fun getTotalCount(): Int

    @Query("SELECT mediaFilePath FROM revision_notes WHERE mediaFilePath IS NOT NULL AND mediaFilePath != ''")
    suspend fun getAllMediaPaths(): List<String>

    @Upsert
    suspend fun upsert(note: RevisionNoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<RevisionNoteEntity>)

    @Query("SELECT * FROM revision_notes WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedNotes(): List<RevisionNoteEntity>

    @Delete
    suspend fun delete(note: RevisionNoteEntity)

    @Query("DELETE FROM revision_notes WHERE deckId = :deckId")
    suspend fun deleteNotesForDeck(deckId: String)

    @Query("DELETE FROM revision_notes")
    suspend fun deleteAll()
}
