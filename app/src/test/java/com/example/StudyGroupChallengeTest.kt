package com.example

import com.example.data.challenge.FriendChallenge
import com.example.data.challenge.FriendChallengeRepository
import com.example.data.challenge.FriendChallengeRepository.Companion.challengeFromJson
import com.example.data.challenge.FriendChallengeRepository.Companion.mergeChallenges
import com.example.data.challenge.FriendChallengeRepository.Companion.toJson
import com.example.data.challenge.FriendChallengeStatus
import com.example.data.challenge.StudyGroupMember
import com.example.data.challenge.StudyGroupMessage
import com.example.data.challenge.StudyGroupNudge
import com.example.data.challenge.StudyMemberRoleStatus
import com.example.data.challenge.StudyMemberTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudyGroupChallengeTest {

    @Test
    fun `multi-friend study group serializes and merges members, messages and nudges`() {
        val membersA = listOf(
            StudyGroupMember(
                uid = "me",
                name = "Youcef",
                minutes = 25,
                timerState = StudyMemberTimerState.FOCUSING,
                roleStatus = StudyMemberRoleStatus.ACTIVE,
                updatedAt = 1000L
            ),
            StudyGroupMember(
                uid = "f1",
                name = "Amine",
                minutes = 10,
                timerState = StudyMemberTimerState.PAUSED,
                roleStatus = StudyMemberRoleStatus.ACTIVE,
                updatedAt = 1000L
            )
        )
        val membersB = listOf(
            StudyGroupMember(
                uid = "me",
                name = "Youcef",
                minutes = 30,
                timerState = StudyMemberTimerState.FOCUSING,
                roleStatus = StudyMemberRoleStatus.ACTIVE,
                updatedAt = 2000L
            ),
            StudyGroupMember(
                uid = "f1",
                name = "Amine",
                minutes = 15,
                timerState = StudyMemberTimerState.PAUSED,
                roleStatus = StudyMemberRoleStatus.ACTIVE,
                updatedAt = 2000L
            ),
            StudyGroupMember(
                uid = "f2",
                name = "Lina",
                minutes = 40,
                timerState = StudyMemberTimerState.FOCUSING,
                roleStatus = StudyMemberRoleStatus.ACTIVE,
                updatedAt = 2000L
            )
        )

        val msg1 = StudyGroupMessage("m1", "me", "Youcef", "🔥", 1100L)
        val msg2 = StudyGroupMessage("m2", "f2", "Lina", "Let's finish this chapter!", 2100L)
        val nudge1 = StudyGroupNudge("n1", "ch_1", "me", "Youcef", "f1", "Amine", 2200L)

        val challengeA = FriendChallenge(
            id = "2026-10-26_me_1000",
            challengerUid = "me",
            challengerName = "Youcef",
            challengerMinutes = 25,
            opponentUid = "f1",
            opponentName = "Amine",
            opponentMinutes = 10,
            dayKey = "2026-10-26",
            createdAt = 1000L,
            acceptedAt = 1000L,
            endAt = 99999L,
            status = FriendChallengeStatus.ACTIVE,
            members = membersA,
            messages = listOf(msg1),
            nudges = emptyList()
        )

        val challengeB = challengeA.copy(
            challengerMinutes = 30,
            opponentMinutes = 15,
            members = membersB,
            messages = listOf(msg1, msg2),
            nudges = listOf(nudge1)
        )

        val merged = mergeChallenges(challengeA, challengeB)
        assertEquals(3, merged.allMembers().size)
        assertEquals(30, merged.myMinutes("me"))
        assertEquals(40, merged.friendMinutes("me"))
        assertEquals(2, merged.messages.size)
        assertEquals(1, merged.nudges.size)
        assertTrue(merged.allMembers().first { it.uid == "f1" }.isPausedOrIdle)
        assertFalse(merged.allMembers().first { it.uid == "f2" }.isPausedOrIdle)

        // Verify JSON round-trip preserves members, messages, and nudges
        val roundTrip = challengeFromJson(merged.toJson())
        assertEquals(3, roundTrip.allMembers().size)
        assertEquals(2, roundTrip.messages.size)
        assertEquals("Let's finish this chapter!", roundTrip.messages.last().text)
        assertEquals(1, roundTrip.nudges.size)
        assertEquals("f1", roundTrip.nudges.first().toUid)
    }

    @Test
    fun `re-launching a challenge after surrendering creates distinct active challenge`() {
        val surrendered = FriendChallenge(
            id = "2026-10-26_me_1000",
            challengerUid = "me",
            challengerName = "Youcef",
            challengerMinutes = 12,
            opponentUid = "f1",
            opponentName = "Amine",
            opponentMinutes = 20,
            dayKey = "2026-10-26",
            createdAt = 1000L,
            acceptedAt = 1000L,
            endAt = 99999L,
            finishedAt = 1500L,
            status = FriendChallengeStatus.SURRENDERED,
            surrenderedByUid = "me",
            winnerUid = "f1"
        )

        val relaunched = FriendChallenge(
            id = "2026-10-26_me_2000",
            challengerUid = "me",
            challengerName = "Youcef",
            challengerMinutes = 12,
            opponentUid = "f1",
            opponentName = "Amine",
            opponentMinutes = 20,
            dayKey = "2026-10-26",
            createdAt = 2000L,
            acceptedAt = 2000L,
            endAt = 99999L,
            status = FriendChallengeStatus.ACTIVE
        )

        assertTrue(surrendered.didISurrender("me"))
        assertFalse(relaunched.didISurrender("me"))
        assertTrue(relaunched.hasActiveParticipant("me"))
        assertTrue(relaunched.hasActiveParticipant("f1"))
        assertFalse(surrendered.id == relaunched.id)
    }

    @Test
    fun `messages are automatically deleted when challenge ends or when cleared by creator`() {
        val msgOld = StudyGroupMessage("m1", "me", "Youcef", "Hello group!", 1000L)
        val msgBeforeClear = StudyGroupMessage("m2", "f1", "Amine", "Focusing now", 1500L)
        val msgAfterClear = StudyGroupMessage("m3", "f1", "Amine", "New message after clear", 2500L)

        val activeWithOldMessages = FriendChallenge(
            id = "2026-10-26_me_1000",
            challengerUid = "me",
            challengerName = "Youcef",
            challengerMinutes = 25,
            opponentUid = "f1",
            opponentName = "Amine",
            opponentMinutes = 20,
            dayKey = "2026-10-26",
            createdAt = 900L,
            acceptedAt = 900L,
            endAt = 99999L,
            status = FriendChallengeStatus.ACTIVE,
            messages = listOf(msgOld, msgBeforeClear),
            messagesClearedAt = 0L
        )

        val clearedByCreator = activeWithOldMessages.copy(
            messages = listOf(msgAfterClear),
            messagesClearedAt = 2000L
        )

        // Merging a stale peer snapshot with creator-cleared snapshot must strip messages <= 2000L
        val mergedAfterClear = mergeChallenges(activeWithOldMessages, clearedByCreator)
        assertEquals(2000L, mergedAfterClear.messagesClearedAt)
        assertEquals(1, mergedAfterClear.messages.size)
        assertEquals("m3", mergedAfterClear.messages.first().id)

        // When challenge ends (COMPLETED or SURRENDERED), all messages are wiped and never resurrected on merge
        val endedChallenge = clearedByCreator.copy(
            status = FriendChallengeStatus.COMPLETED,
            finishedAt = 99999L,
            messages = emptyList(),
            nudges = emptyList(),
            messagesClearedAt = 99999L
        )
        val mergedWithEnded = mergeChallenges(activeWithOldMessages, endedChallenge)
        assertEquals(FriendChallengeStatus.COMPLETED, mergedWithEnded.status)
        assertTrue(mergedWithEnded.messages.isEmpty())
        assertTrue(mergedWithEnded.nudges.isEmpty())
    }
}
