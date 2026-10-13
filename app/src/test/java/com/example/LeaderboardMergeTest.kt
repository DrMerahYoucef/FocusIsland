package com.example

import com.example.ui.screen.community.LeaderboardEntry
import com.example.ui.screen.community.LeaderboardSort
import com.example.ui.screen.community.UserProfile
import com.example.ui.screen.community.mergeRanking
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardMergeTest {
    @Test
    fun friendsMissingFromTheTopListAreAddedAndRanked() {
        val ranked = listOf(LeaderboardEntry("me", "youcef", points = 300), LeaderboardEntry("y", "youyou", points = 65))
        val people = listOf(
            UserProfile(uid = "me", username = "youcef", points = 358), // live profile wins
            UserProfile(uid = "s", username = "salmi", points = 5),
            UserProfile(uid = "b", username = "Boukhari", points = 0)
        )
        val merged = mergeRanking(ranked, people, LeaderboardSort.POINTS)
        assertEquals(listOf("me", "y", "s", "b"), merged.map { it.uid })
        assertEquals(358, merged.first().points)
    }
}
