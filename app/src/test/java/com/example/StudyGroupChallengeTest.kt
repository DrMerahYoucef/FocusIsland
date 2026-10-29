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

        // When creator deletes the Study Group (CANCELLED), it overrides any cached ACTIVE copy on friends' phones
        val cancelledByCreator = activeWithOldMessages.copy(
            status = FriendChallengeStatus.CANCELLED,
            finishedAt = 3000L,
            messages = emptyList(),
            nudges = emptyList(),
            messagesClearedAt = 3000L
        )
        val mergedWithCancelled = mergeChallenges(activeWithOldMessages, cancelledByCreator)
        assertEquals(FriendChallengeStatus.CANCELLED, mergedWithCancelled.status)
        assertTrue(mergedWithCancelled.messages.isEmpty())
        assertTrue(mergedWithCancelled.nudges.isEmpty())
    }

    @Test
    fun `invited member is only added to accepted leaderboard after accepting and stays offered for 30 min when declined`() {
        val t0 = 1_000_000L
        val creator = StudyGroupMember(
            uid = "creator",
            name = "Youcef",
            minutes = 45,
            timerState = StudyMemberTimerState.FOCUSING,
            roleStatus = StudyMemberRoleStatus.ACTIVE,
            invitedAt = t0,
            updatedAt = t0
        )
        val invitedFriend = StudyGroupMember(
            uid = "friend1",
            name = "Salmi",
            minutes = 0,
            timerState = StudyMemberTimerState.IDLE,
            roleStatus = StudyMemberRoleStatus.INVITED,
            invitedAt = t0,
            updatedAt = t0
        )

        val group = FriendChallenge(
            id = "2026-10-26_creator_$t0",
            challengerUid = "creator",
            challengerName = "Youcef",
            challengerMinutes = 45,
            opponentUid = "friend1",
            opponentName = "Salmi",
            opponentMinutes = 0,
            dayKey = "2026-10-26",
            createdAt = t0,
            acceptedAt = t0,
            endAt = t0 + 86_400_000L,
            status = FriendChallengeStatus.ACTIVE,
            members = listOf(creator, invitedFriend)
        )

        // 1. Before accepting: only creator is in acceptedMembers(); friend1 is in pendingOrExpiredInviteMembers()
        assertEquals(1, group.acceptedMembers().size)
        assertEquals("creator", group.acceptedMembers().first().uid)
        assertFalse(group.hasActiveParticipant("friend1"))
        assertTrue(group.hasValidInvite("friend1", t0 + 5 * 60_000L))
        assertTrue(group.isFreshInviteFor("friend1", t0 + 5 * 60_000L))
        assertFalse(group.isDeclinedOfferFor("friend1", t0 + 5 * 60_000L))

        // 2. Friend declines at +5 min: status becomes DECLINED, still offered for remaining 25 minutes
        val declinedMember = invitedFriend.copy(
            roleStatus = StudyMemberRoleStatus.DECLINED,
            updatedAt = t0 + 5 * 60_000L
        )
        val groupAfterDecline = group.copy(members = listOf(creator, declinedMember))
        assertTrue(groupAfterDecline.hasValidInvite("friend1", t0 + 10 * 60_000L))
        assertTrue(groupAfterDecline.isDeclinedOfferFor("friend1", t0 + 10 * 60_000L))
        assertEquals(20 * 60_000L, groupAfterDecline.inviteRemainingMsFor("friend1", t0 + 10 * 60_000L))

        // 3. At +30 minutes + 1ms: invitation expires and is no longer valid
        val expiredTime = t0 + FriendChallengeRepository.INVITE_EXPIRY_MS + 1L
        assertFalse(groupAfterDecline.hasValidInvite("friend1", expiredTime))
        assertFalse(groupAfterDecline.isDeclinedOfferFor("friend1", expiredTime))
        assertEquals(0L, groupAfterDecline.inviteRemainingMsFor("friend1", expiredTime))

        // 4. Creator re-sends invitation at expiredTime + 10_000L: friend gets a fresh 30m window
        val resentTime = expiredTime + 10_000L
        val resentMember = declinedMember.copy(
            roleStatus = StudyMemberRoleStatus.INVITED,
            invitedAt = resentTime,
            updatedAt = resentTime
        )
        val groupAfterResend = mergeChallenges(groupAfterDecline, groupAfterDecline.copy(members = listOf(creator, resentMember)))
        assertTrue(groupAfterResend.hasValidInvite("friend1", resentTime + 1_000L))
        assertTrue(groupAfterResend.isFreshInviteFor("friend1", resentTime + 1_000L))
        assertFalse(groupAfterResend.isDeclinedOfferFor("friend1", resentTime + 1_000L))

        // 5. Friend accepts within the new 30m window: joins acceptedMembers()
        val acceptedMember = resentMember.copy(
            roleStatus = StudyMemberRoleStatus.ACTIVE,
            updatedAt = resentTime + 30_000L
        )
        val groupAfterAccept = mergeChallenges(groupAfterResend, groupAfterResend.copy(members = listOf(creator, acceptedMember)))
        assertEquals(2, groupAfterAccept.acceptedMembers().size)
        assertTrue(groupAfterAccept.hasActiveParticipant("friend1"))
        assertTrue(groupAfterAccept.pendingOrExpiredInviteMembers().isEmpty())
    }

    @Test
    fun `target hours challenge detects immediate winner when target reached and serializes targetMinutes`() {
        val t0 = 5_000_000L
        val creator = StudyGroupMember(
            uid = "creator",
            name = "Youcef",
            minutes = 60,
            timerState = StudyMemberTimerState.FOCUSING,
            roleStatus = StudyMemberRoleStatus.ACTIVE,
            invitedAt = t0,
            updatedAt = t0 + 1_000L
        )
        val friend = StudyGroupMember(
            uid = "friend1",
            name = "Amine",
            minutes = 90,
            timerState = StudyMemberTimerState.FOCUSING,
            roleStatus = StudyMemberRoleStatus.ACTIVE,
            invitedAt = t0,
            updatedAt = t0 + 2_000L
        )

        val openChallenge = FriendChallenge(
            id = "2026-10-28_creator_$t0",
            challengerUid = "creator",
            challengerName = "Youcef",
            challengerMinutes = 60,
            opponentUid = "friend1",
            opponentName = "Amine",
            opponentMinutes = 90,
            dayKey = "2026-10-28",
            createdAt = t0,
            acceptedAt = t0,
            endAt = t0 + 86_400_000L,
            targetMinutes = 0,
            status = FriendChallengeStatus.ACTIVE,
            members = listOf(creator, friend)
        )
        assertFalse(openChallenge.isTargetHoursChallenge)
        assertEquals(null, openChallenge.findTargetReachedWinnerUid())

        val targetChallenge = openChallenge.copy(targetMinutes = 180) // 3 hours race
        assertTrue(targetChallenge.isTargetHoursChallenge)
        assertEquals(null, targetChallenge.findTargetReachedWinnerUid())

        // Friend reaches 180 minutes first at t0 + 5_000L
        val friendReached = friend.copy(minutes = 180, updatedAt = t0 + 5_000L)
        val creatorReachedLater = creator.copy(minutes = 185, updatedAt = t0 + 9_000L)
        val updatedChallenge = targetChallenge.copy(
            challengerMinutes = 185,
            opponentMinutes = 180,
            members = listOf(creatorReachedLater, friendReached)
        )

        // Earliest participant to reach >= 180 minutes wins
        assertEquals("friend1", updatedChallenge.findTargetReachedWinnerUid())

        // Verify JSON round-trip and merge preserve targetMinutes
        val roundTrip = challengeFromJson(updatedChallenge.toJson())
        assertEquals(180, roundTrip.targetMinutes)
        assertTrue(roundTrip.isTargetHoursChallenge)
        assertEquals("friend1", roundTrip.findTargetReachedWinnerUid())

        val merged = mergeChallenges(openChallenge, updatedChallenge)
        assertEquals(180, merged.targetMinutes)
    }

    @Test
    fun `creator adding extra focus time extends targetMinutes and propagates through mergeChallenges`() {
        val t0 = 7_000_000L
        val creator = StudyGroupMember(
            uid = "creator",
            name = "Youcef",
            minutes = 110,
            timerState = StudyMemberTimerState.FOCUSING,
            roleStatus = StudyMemberRoleStatus.ACTIVE,
            invitedAt = t0,
            updatedAt = t0 + 1_000L
        )
        val friend = StudyGroupMember(
            uid = "friend1",
            name = "Salmi",
            minutes = 95,
            timerState = StudyMemberTimerState.FOCUSING,
            roleStatus = StudyMemberRoleStatus.ACTIVE,
            invitedAt = t0,
            updatedAt = t0 + 2_000L
        )

        // Initial 2-hour (120m) challenge created by Youcef
        val initialChallenge = FriendChallenge(
            id = "2026-10-29_creator_$t0",
            challengerUid = "creator",
            challengerName = "Youcef",
            challengerMinutes = 110,
            opponentUid = "friend1",
            opponentName = "Salmi",
            opponentMinutes = 95,
            dayKey = "2026-10-29",
            createdAt = t0,
            acceptedAt = t0,
            endAt = t0 + 86_400_000L,
            targetMinutes = 120,
            status = FriendChallengeStatus.ACTIVE,
            members = listOf(creator, friend)
        )
        assertTrue(initialChallenge.isChallenger("creator"))
        assertFalse(initialChallenge.isChallenger("friend1"))
        assertEquals(null, initialChallenge.findTargetReachedWinnerUid())

        // Creator adds +60m (1 hour) -> new target is 180m (3 hours) with announcement message
        val addTimeMsg = StudyGroupMessage(
            id = "creator_${t0 + 3_000L}",
            senderUid = "creator",
            senderName = "Youcef",
            text = "⏱️ Added +1h to the challenge goal (New target: 3h)",
            createdAt = t0 + 3_000L
        )
        val extendedChallenge = initialChallenge.copy(
            targetMinutes = initialChallenge.targetMinutes + 60,
            messages = initialChallenge.messages + addTimeMsg
        )
        assertEquals(180, extendedChallenge.targetMinutes)

        // Merging with an older snapshot from friend1 keeps the higher 180m target and announcement message
        val merged = mergeChallenges(initialChallenge, extendedChallenge)
        assertEquals(180, merged.targetMinutes)
        assertEquals(1, merged.messages.size)

        // Even when creator reaches 125m (which would have won the old 120m goal), challenge continues until 180m
        val after125m = merged.copy(
            challengerMinutes = 125,
            members = listOf(creator.copy(minutes = 125, updatedAt = t0 + 4_000L), friend)
        )
        assertEquals(null, after125m.findTargetReachedWinnerUid())

        // Once creator reaches 180m, creator wins
        val after180m = after125m.copy(
            challengerMinutes = 180,
            members = listOf(creator.copy(minutes = 180, updatedAt = t0 + 8_000L), friend)
        )
        assertEquals("creator", after180m.findTargetReachedWinnerUid())
    }
}


