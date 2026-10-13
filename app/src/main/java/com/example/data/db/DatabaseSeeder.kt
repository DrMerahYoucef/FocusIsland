package com.example.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The one place that knows the app's default rows (radio catalogue + default deck).
 * Every statement is INSERT OR IGNORE, so seeding is safe to run any number of times:
 * on database creation, inside historical migrations, and after a data reset.
 */
object DatabaseSeeder {

    const val DEFAULT_DECK_ID = "default_deck"
    const val DEFAULT_DECK_NAME = "Général"
    const val DEFAULT_DECK_COLOR = "#4CAF50"

    private data class SeedStation(
        val id: String,
        val name: String,
        val country: String,
        val categoryId: String,
        val streamUrl: String,
        val description: String
    )

    private val categories = listOf("STUDY" to "Study", "GLOBAL" to "Global")

    private val stations = listOf(
        SeedStation("lofi_hiphop", "Lo-Fi Hip Hop", "🌍 Global", "STUDY", "https://streams.ilovemusic.de/iloveradio17.mp3", "Chill beats to study and relax to"),
        SeedStation("study_classical", "Classical Focus", "🌍 Global", "STUDY", "https://live.musopen.org:8085/streamvbr0", "Classical music — royalty free"),
        SeedStation("cafe_jazz", "Jazz Café", "🌍 Global", "STUDY", "https://streams.ilovemusic.de/iloveradio29.mp3", "Smooth jazz for deep work"),
        SeedStation("ambient_space", "Ambient Space", "🌍 Global", "STUDY", "https://ice1.somafm.com/deepspaceone-128-mp3", "Deep space ambient for long sessions"),
        SeedStation("brown_noise", "Brown Noise", "🌍 Global", "STUDY", "https://ice1.somafm.com/darkzone-128-mp3", "Noise masking for maximum focus"),
        SeedStation("piano_study", "Piano Study", "🌍 Global", "STUDY", "https://streams.ilovemusic.de/iloveradio2.mp3", "Solo piano — no lyrics, pure focus"),
        SeedStation("bbc_radio4", "BBC Radio 4", "🇬🇧 UK", "GLOBAL", "https://stream.live.vc.bbcmedia.co.uk/bbc_radio_fourfm", "Talk radio — news and culture"),
        SeedStation("france_inter", "France Inter", "🇫🇷 France", "GLOBAL", "https://icecast.radiofrance.fr/franceinter-hifi.aac", "French public radio"),
        SeedStation("monte_carlo", "Radio Monte Carlo", "🇫🇷 France", "GLOBAL", "https://icy.unitedradio.it/RMC.mp3", "French pop and hits"),
        SeedStation("soma_groove", "SomaFM Groove Salad", "🌍 Global", "GLOBAL", "https://ice1.somafm.com/groovesalad-128-mp3", "A nicely chilled plate of ambient")
    )

    /**
     * Seeds the default radio categories and stations.
     * [withFavourites] also marks every default station as a favourite (fresh installs and resets).
     */
    fun seedRadio(db: SupportSQLiteDatabase, withFavourites: Boolean) {
        categories.forEach { (id, name) ->
            db.execSQL(
                "INSERT OR IGNORE INTO radio_categories (id, name, isCustom) VALUES (?, ?, 0)",
                arrayOf<Any>(id, name)
            )
        }
        stations.forEach { s ->
            db.execSQL(
                "INSERT OR IGNORE INTO radio_stations (id, name, country, categoryId, streamUrl, fallbackUrl, logoUrl, description, isCustom) " +
                    "VALUES (?, ?, ?, ?, ?, '', '', ?, 0)",
                arrayOf<Any>(s.id, s.name, s.country, s.categoryId, s.streamUrl, s.description)
            )
        }
        if (withFavourites) {
            val now = System.currentTimeMillis()
            stations.forEachIndexed { index, s ->
                db.execSQL(
                    "INSERT OR IGNORE INTO favourite_stations (stationId, savedAt) VALUES (?, ?)",
                    arrayOf<Any>(s.id, now - index)
                )
            }
        }
    }

    fun seedDefaultDeck(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT OR IGNORE INTO revision_decks (id, name, colorHex, createdAt) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(DEFAULT_DECK_ID, DEFAULT_DECK_NAME, DEFAULT_DECK_COLOR, System.currentTimeMillis())
        )
    }

    fun seedAll(db: SupportSQLiteDatabase) {
        seedRadio(db, withFavourites = true)
        seedDefaultDeck(db)
    }
}
