package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.db.DatabaseSeeder
import com.example.data.db.entity.RevisionDeckEntity
import com.example.data.db.entity.RevisionNoteEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

enum class ImportMode { MERGE, REPLACE_ALL }

class RevisionRepository(private val database: AppDatabase) {

    private val deckDao = database.revisionDeckDao()
    private val noteDao = database.revisionNoteDao()

    val allDecks: Flow<List<RevisionDeckEntity>> = deckDao.getAllDecks()
    val allNotes: Flow<List<RevisionNoteEntity>> = noteDao.getAllNotes()

    /** Re-evaluated every minute, so cards that become due during the day show up without a restart. */
    val dueNotes: Flow<List<RevisionNoteEntity>> =
        combine(allNotes, minuteTicker()) { notes, now ->
            notes.filter { it.dueDate <= now }.sortedBy { it.dueDate }
        }.distinctUntilChanged()

    fun getNotesForDeck(deckId: String): Flow<List<RevisionNoteEntity>> = noteDao.getNotesForDeck(deckId)

    suspend fun getDueCount(): Int = noteDao.getDueCount(System.currentTimeMillis())
    suspend fun getTotalCount(): Int = noteDao.getTotalCount()
    suspend fun getAllDecksOnce(): List<RevisionDeckEntity> = deckDao.getAllDecksOnce()
    suspend fun getAllNotesOnce(): List<RevisionNoteEntity> = noteDao.getAllNotesOnce()
    suspend fun getAllMediaPaths(): List<String> = noteDao.getAllMediaPaths()

    /**
     * Fixed list of cards for one review session. Taken once at session start so grading
     * a card (which moves its due date) never reshuffles or shrinks the queue mid-session.
     * [deckId] == [ALL_DECKS_ID] reviews every deck. [includeNotDue] = practise ahead.
     */
    suspend fun buildReviewQueue(deckId: String, includeNotDue: Boolean, limit: Int): List<RevisionNoteEntity> {
        val now = System.currentTimeMillis()
        val all = deckId == ALL_DECKS_ID
        val notes = when {
            includeNotDue && all -> noteDao.getAllNotesOnce().sortedBy { it.dueDate }
            includeNotDue -> noteDao.getNotesForDeckOnce(deckId)
            all -> noteDao.getDueNotesOnce(now)
            else -> noteDao.getDueNotesForDeckOnce(deckId, now)
        }
        return notes.take(limit.coerceAtLeast(1))
    }

    suspend fun upsertDeck(deck: RevisionDeckEntity) = deckDao.upsert(deck)

    /** Deletes a deck and every card in it atomically. Returns the media paths the caller must delete. */
    suspend fun deleteDeckAndNotes(deckId: String): List<String> = database.withTransaction {
        val media = noteDao.getNotesForDeckOnce(deckId).mapNotNull { it.mediaFilePath?.takeIf(String::isNotBlank) }
        noteDao.deleteNotesForDeck(deckId)
        deckDao.getDeckById(deckId)?.let { deckDao.delete(it) }
        media
    }

    /** Replaces every deck and card in one transaction (backup restore, "replace" mode). */
    suspend fun replaceAll(decks: List<RevisionDeckEntity>, notes: List<RevisionNoteEntity>) = database.withTransaction {
        noteDao.deleteAll()
        deckDao.deleteAll()
        decks.forEach { deckDao.upsert(it) }
        ensureDefaultDeck()
        noteDao.upsertAll(notes)
    }

    /** Recreates the default deck if it is missing. Returns its id. */
    suspend fun ensureDefaultDeck(): String {
        if (deckDao.getDeckById(DatabaseSeeder.DEFAULT_DECK_ID) == null) {
            deckDao.upsert(
                RevisionDeckEntity(
                    id = DatabaseSeeder.DEFAULT_DECK_ID,
                    name = DatabaseSeeder.DEFAULT_DECK_NAME,
                    colorHex = DatabaseSeeder.DEFAULT_DECK_COLOR
                )
            )
        }
        return DatabaseSeeder.DEFAULT_DECK_ID
    }

    suspend fun getNoteById(noteId: String): RevisionNoteEntity? = noteDao.getById(noteId)
    suspend fun getDeckById(deckId: String): RevisionDeckEntity? = deckDao.getDeckById(deckId)

    suspend fun upsertNote(note: RevisionNoteEntity) = noteDao.upsert(note)
    suspend fun upsertNotes(notes: List<RevisionNoteEntity>) = noteDao.upsertAll(notes)
    suspend fun deleteNote(note: RevisionNoteEntity) = noteDao.delete(note)

    suspend fun getUnsyncedNotes() = noteDao.getUnsyncedNotes()

    companion object {
        /** Pseudo deck id used by the UI for "all decks". */
        const val ALL_DECKS_ID = "ALL"
    }
}

/** Emits the current time immediately, then once a minute while collected. */
private fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000L)
    }
}
