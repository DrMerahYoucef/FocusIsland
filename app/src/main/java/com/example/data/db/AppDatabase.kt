package com.example.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.db.dao.BlockedAppDao
import com.example.data.db.dao.ExamDao
import com.example.data.db.dao.FavouriteStationDao
import com.example.data.db.dao.RadioDao
import com.example.data.db.dao.RevisionDeckDao
import com.example.data.db.dao.RevisionNoteDao
import com.example.data.db.dao.SessionDao
import com.example.data.db.entity.BlockedAppEntity
import com.example.data.db.entity.CategoryEntity
import com.example.data.db.entity.ExamEntity
import com.example.data.db.entity.FavouriteStationEntity
import com.example.data.db.entity.RevisionDeckEntity
import com.example.data.db.entity.RevisionNoteEntity
import com.example.data.db.entity.SessionEntity
import com.example.data.db.entity.StationEntity
import com.example.data.repository.NoteBlock
import com.example.data.repository.NoteBlocksSerializer
import org.json.JSONArray

@Database(
    entities = [
        SessionEntity::class,
        ExamEntity::class,
        BlockedAppEntity::class,
        FavouriteStationEntity::class,
        CategoryEntity::class,
        StationEntity::class,
        RevisionDeckEntity::class,
        RevisionNoteEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun examDao(): ExamDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun favouriteStationDao(): FavouriteStationDao
    abstract fun radioDao(): RadioDao
    abstract fun revisionDeckDao(): RevisionDeckDao
    abstract fun revisionNoteDao(): RevisionNoteDao

    companion object {
        private const val TAG = "AppDatabase"
        const val DATABASE_NAME = "focusflow_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_apps (
                        packageName TEXT PRIMARY KEY NOT NULL,
                        appName TEXT NOT NULL,
                        appIconBase64 TEXT NOT NULL,
                        blockNotifications INTEGER NOT NULL DEFAULT 1,
                        blockLaunch INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favourite_stations (
                        stationId TEXT PRIMARY KEY NOT NULL,
                        savedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS radio_categories (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        isCustom INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS radio_stations (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        country TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        streamUrl TEXT NOT NULL,
                        fallbackUrl TEXT NOT NULL DEFAULT '',
                        logoUrl TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL,
                        isCustom INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(categoryId) REFERENCES radio_categories(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_radio_stations_categoryId ON radio_stations(categoryId)")
                DatabaseSeeder.seedRadio(db, withFavourites = false)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS revision_decks (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        colorHex TEXT NOT NULL DEFAULT '#4CAF50',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS revision_notes (
                        id TEXT PRIMARY KEY NOT NULL,
                        deckId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        contentMarkdown TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        easeFactor REAL NOT NULL DEFAULT 2.5,
                        intervalDays INTEGER NOT NULL DEFAULT 0,
                        repetitions INTEGER NOT NULL DEFAULT 0,
                        dueDate INTEGER NOT NULL,
                        lastReviewedAt INTEGER,
                        firestoreId TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING_UPLOAD'
                    )
                    """.trimIndent()
                )
                DatabaseSeeder.seedDefaultDeck(db)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE revision_notes ADD COLUMN contentBlocksJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE revision_notes ADD COLUMN plainTextPreview TEXT NOT NULL DEFAULT ''")
                // Best-effort backfill. Notes whose text contained line breaks produced invalid
                // JSON here; MIGRATION_7_8 repairs those rows.
                db.execSQL(
                    """
                    UPDATE revision_notes
                    SET contentBlocksJson = '[{"type":"text","content":"' || replace(replace(contentMarkdown, '\', '\\'), '"', '\"') || '","highlights":[]}]',
                        plainTextPreview = contentMarkdown
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE revision_notes ADD COLUMN mediaType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE revision_notes ADD COLUMN mediaFilePath TEXT DEFAULT NULL")
            }
        }

        /** v8: query indices + repair of card bodies that the v6 backfill left as invalid JSON. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_revision_notes_deckId_dueDate ON revision_notes(deckId, dueDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_revision_notes_dueDate ON revision_notes(dueDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_date ON sessions(date)")
                repairLegacyNoteBodies(db)
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
        )

        private fun repairLegacyNoteBodies(db: SupportSQLiteDatabase) {
            val broken = mutableListOf<Pair<String, String>>()
            db.query("SELECT id, contentBlocksJson, contentMarkdown FROM revision_notes WHERE contentMarkdown != ''").use { c ->
                while (c.moveToNext()) {
                    val json = c.getString(1).orEmpty()
                    val valid = json.isBlank() || runCatching { JSONArray(json) }.isSuccess
                    if (!valid) broken += c.getString(0) to c.getString(2)
                }
            }
            broken.forEach { (id, markdown) ->
                val repaired = NoteBlocksSerializer.toJson(listOf(NoteBlock.TextBlock(content = markdown)))
                db.execSQL("UPDATE revision_notes SET contentBlocksJson = ? WHERE id = ?", arrayOf<Any>(repaired, id))
            }
            if (broken.isNotEmpty()) Log.i(TAG, "Repaired ${broken.size} legacy card bodies")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            DatabaseSeeder.seedAll(db)
                        }
                    })
                    // No destructive fallback: a missing migration must fail loudly in testing,
                    // never silently erase the user's cards and history.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
