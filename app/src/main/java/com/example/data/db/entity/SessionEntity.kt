package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    indices = [Index(value = ["date"])]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,              // epoch millis when the session ended (also the sync key)
    val durationSeconds: Int,    // actual focus seconds completed
    val completed: Boolean,      // false if skipped/ended early
    val focusScore: Int          // 1 point per completed focus minute
)
