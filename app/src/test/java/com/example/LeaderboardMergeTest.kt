package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.challenge.StudyMemberTimerState
import com.example.ui.screen.community.LeaderboardEntry
import com.example.ui.screen.community.LeaderboardSort
import com.example.ui.screen.community.UserProfile
import com.example.ui.screen.community.friendStatusColor
import com.example.ui.screen.community.friendStatusKey
import com.example.ui.screen.community.mergeRanking
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardMergeTest {
    @Test
    fun friendsMissingFromTheTopListAreAddedAndRanked() {
        val ranked = listOf(LeaderboardEntry("me", "youcef", points = 300), LeaderboardEntry("y", "youyou", points = 65))
        val people = listOf(
            UserProfile(uid = "me", username = "youcef", points = 358, timerState = StudyMemberTimerState.FOCUSING), // live profile wins
            UserProfile(uid = "s", username = "salmi", points = 5, timerState = StudyMemberTimerState.PAUSED),
            UserProfile(uid = "b", username = "Boukhari", points = 0, timerState = StudyMemberTimerState.IDLE)
        )
        val merged = mergeRanking(ranked, people, LeaderboardSort.POINTS)
        assertEquals(listOf("me", "y", "s", "b"), merged.map { it.uid })
        assertEquals(358, merged.first().points)
        assertEquals(StudyMemberTimerState.FOCUSING, merged.first { it.uid == "me" }.timerState)
        assertEquals(StudyMemberTimerState.PAUSED, merged.first { it.uid == "s" }.timerState)
        assertEquals(StudyMemberTimerState.IDLE, merged.first { it.uid == "b" }.timerState)
    }

    @Test
    fun friendStatusColorsMatchFocusingPausedAndOffline() {
        // Green for focusing
        assertEquals(Color(0xFF22C55E), friendStatusColor(StudyMemberTimerState.FOCUSING))
        assertEquals("focusing", friendStatusKey(StudyMemberTimerState.FOCUSING))

        // Orange for paused or on break
        assertEquals(Color(0xFFF59E0B), friendStatusColor(StudyMemberTimerState.PAUSED))
        assertEquals(Color(0xFFF59E0B), friendStatusColor(StudyMemberTimerState.ON_BREAK))
        assertEquals("paused", friendStatusKey(StudyMemberTimerState.PAUSED))

        // Red for offline / idle
        assertEquals(Color(0xFFEF4444), friendStatusColor(StudyMemberTimerState.IDLE))
        assertEquals("offline", friendStatusKey(StudyMemberTimerState.IDLE))
    }
}

