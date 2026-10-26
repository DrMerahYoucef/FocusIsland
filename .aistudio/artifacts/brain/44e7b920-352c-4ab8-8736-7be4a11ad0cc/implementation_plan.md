# Study Group & Challenge Invitation Lifecycle (30-Minute Offer & Expiration)

When a user invites one or more friends to a Study Group or Challenge, each invited friend receives an instant Android notification (sound + vibration). Invited friends are only added to the active Study Group leaderboard after they accept. If they decline, the invitation remains available in a compact Pending Invites section for the remainder of the 30-minute window; if they neither accept nor join within 30 minutes, the invitation expires and the sender must re-send the invitation to add them.

## User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed user preferences from Phase 1:
> - **Declined Invitation Behavior (30-Minute Window)**: When a friend taps **Decline** on an invitation, it stays offered in a compact **Pending Invites** section with a live 30-minute countdown so they can still join before it expires.
> - **Group Creator View of Pending Invites**: Only **accepted (`ACTIVE`)** members appear in the Study Group horloge leaderboard; invited friends who haven't joined yet appear as compact **Pending Invite status chips** (showing their countdown or an **Expired · Re-invite** action).

## 1. Overview & Core Concept

- **What It Does**:
  1. **Instant Invite Notification**: Inviting a friend to a Study Group or Challenge triggers a high-priority Android notification (sound + vibration) on the invited friend's phone.
  2. **Accept to Join**: Invited friends start with `INVITED` status instead of `ACTIVE`. They are only added to the Study Group leaderboard and chat room once they tap **Accept**.
  3. **30-Minute Offer Window on Decline**: Tapping **Decline** marks the member's invite as `DECLINED` without deleting it; for 30 minutes from `invitedAt`, the offer remains accessible in a compact **Pending Invites** card with a live countdown (`Expires in MM:SS`) and an **Accept** button in case they change their mind.
  4. **30-Minute Expiration & Re-Invite**: If 30 minutes pass (`now >= invitedAt + 30 min`) without the friend accepting, the invitation automatically expires. Expired invites disappear from the friend's incoming offers and become selectable again in the sender's **Add Friends** sheet (plus a one-tap **Re-send** chip in the group header).

## 2. Design & Visual Direction

- **Incoming Invitation Banner & Compact Declined Offer Card**:
  - **Fresh Invite (`INVITED`)**: Prominent `secondaryContainer` card displaying the inviter's name, a live `Expires in MM:SS` badge, **Decline**, and **Accept & Start**.
  - **Declined but Still Offered (`DECLINED` within 30 min)**: Compact `surfaceContainer` row under a **Pending Invites (30 min offer)** header showing the inviter's name, remaining time (`MM:SS left`), and a tonal **Accept** button.
- **Creator & Group Header Pending Chips**:
  - Below the Study Group header, non-accepted invitees are shown as compact status chips (e.g., `⏳ Amine · 24:10` while pending, or `⌛ Amine · Expired (Tap to re-send)` once 30 minutes elapse) while keeping the horloge leaderboard strictly for accepted members.

## 3. System Architecture & Data Flow

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   STUDY GROUP 30-MIN INVITATION LIFECYCLE                  │
└────────────────────────────────────────────────────────────────────────────┘
  Sender taps "Add Friends" / "Challenge"
         │
         ▼
  StudyGroupMember(uid, roleStatus = INVITED, invitedAt = now)
         │
         ├──► Friend's Firestore Listener detects new (challengeId, invitedAt)
         │          │
         │          ▼
         │    Triggers Android Notification (Sound + Vibration + Deep Link)
         │
         ├──► Friend taps "Accept" (while now < invitedAt + 30 min)
         │          │
         │          ▼
         │    roleStatus = ACTIVE ──► Added to Horloge Leaderboard & Chat Room
         │
         ├──► Friend taps "Decline" (while now < invitedAt + 30 min)
         │          │
         │          ▼
         │    roleStatus = DECLINED ──► Stays offered in Compact Pending Card
         │                              with live 30m countdown until expiry
         │
         └──► 30 Minutes Elapse (now >= invitedAt + 30 min)
                    │
                    ▼
              Invitation Expired:
              • Removed from Friend's Incoming / Offered list
              • Sender can re-invite friend (new invitedAt = now)
```

## 4. Core Functional & Technical Changes

- **Member Invitation State & 30-Minute Expiry (`FriendChallengeRepository`)**:
  - Add `DECLINED` to `StudyMemberRoleStatus` (`ACTIVE`, `INVITED`, `DECLINED`, `SURRENDERED`).
  - Add `invitedAt: Long` to `StudyGroupMember` and persist it in JSON/Firestore (`invitedAt` defaults to `updatedAt` or `challenge.createdAt` for backwards compatibility).
  - Define `INVITE_EXPIRY_MS = 30 * 60_000L` (30 minutes) and helper methods on `StudyGroupMember` and `FriendChallenge`:
    - `isInviteValid(now: Long)`: returns `true` when `roleStatus` is `INVITED` or `DECLINED` and `now - effectiveInvitedAt < INVITE_EXPIRY_MS`.
    - `isInviteExpired(now: Long)`: returns `true` when `roleStatus` is `INVITED` or `DECLINED` and `now - effectiveInvitedAt >= INVITE_EXPIRY_MS`.
    - `acceptedMembers()`: returns only members with `roleStatus == StudyMemberRoleStatus.ACTIVE` (used for the leaderboard and active participant checks).
  - Update `startOrInviteToStudyGroup`:
    - Set the creator's `roleStatus = ACTIVE`, and set each invited friend's `roleStatus = INVITED` and `invitedAt = now`.
    - Allow re-inviting any friend whose previous invite has expired (`isInviteExpired(now)`), resetting their `roleStatus = INVITED` and `invitedAt = now`.
  - Update `declineChallenge`:
    - Instead of marking the challenge permanently abandoned, update the current user's `StudyGroupMember` entry to `roleStatus = DECLINED` (preserving `invitedAt`) and sync to Firestore so the invitation remains offered in the compact Pending Invites section until `invitedAt + 30 min`.
  - Update `mergeChallenges`:
    - When a friend is re-invited (`m.roleStatus == INVITED && m.invitedAt > prev.invitedAt`), allow the newer `INVITED` state to replace a prior `DECLINED` or expired `INVITED` state; when a friend accepts (`m.roleStatus == ACTIVE && m.updatedAt >= prev.invitedAt`), transition to `ACTIVE`.
- **Android Notification on Incoming Invitation**:
  - Track notified invitation keys (`"${challenge.id}_${member.invitedAt}"`) in `SharedPreferences` (`KEY_HANDLED_INVITE_IDS`).
  - When a real-time Firestore snapshot delivers an active 30-minute invitation (`roleStatus == INVITED`) for `myUid` that hasn't been notified yet, trigger `triggerIncomingInviteNotification(challengerName)` with sound, vibration, and tap action to open the Challenges screen.

## 5. Verification Criteria

- **Unit Tests (`StudyGroupChallengeTest`)**:
  - Verify that newly invited friends start as `INVITED` (not `ACTIVE`) and only appear in `acceptedMembers()` after accepting.
  - Verify that declining an invitation keeps it offered (`isInviteValid(now) == true`) for 30 minutes and expires (`isInviteExpired(now) == true`) once 30 minutes pass.
  - Verify that re-sending an invitation after 30 minutes resets `invitedAt` and makes the invitation active again.
- **Build Verification**:
  - Run `compile_applet` and unit tests to confirm zero build or test regressions.
