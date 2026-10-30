### Goal
Enforce real app blocking during focus sessions while keeping the Pomodoro running, show a draggable floating Return to Focus bubble when switching to non-blocked apps, sync challenges to Firebase with a 1st/2nd/3rd podium rankings view for completed challenges and a Challenges Won badge on the Leaderboard, display an interactive Return to Focus toast banner over any screen when nudged by a friend, and clearly distinguish Pomodoro session breaks from paused or non-focusing states in challenges.

### Key Changes

- **1. True App Blocking Without Pausing the Pomodoro**
  - Default newly blocked apps in the App Blocker screen to enable launch blocking immediately when toggled on, and keep the foreground app launch monitor active throughout the entire focus phase (including when the user leaves Focus Island).
  - Polling frequency will be tightened so blocked app launches are detected rapidly.
  - When the user switches to an app on the App Blocker list during a focus session, launch the full-screen blocking overlay (`FocusOverlayActivity`) immediately to block access while keeping the Pomodoro countdown running uninterrupted.

- **2. Unlisted App Switch Auto-Pause & Draggable Floating ("Volant") Notification**
  - When the Pomodoro focus timer is running and the user switches to another app that is *not* in the App Blocker list (while the screen is interactive and unlocked), pause the Pomodoro timer automatically.
  - Display a draggable floating ("volant") Return to Focus bubble overlay (using system overlay window when granted, plus an ongoing high-priority heads-up notification fallback) that the user can move freely across the screen with their finger.
  - Keep the floating Return to Focus bubble visible on screen until the user taps it to return to Focus Island or reopens Focus Island directly, at which point the bubble dismisses and the Pomodoro resumes.

- **3. Firebase Challenge Persistence, Completed Challenge Podium View & Leaderboard Win Badge**
  - Persist all Study Group and Friend Challenges (including final participant standings, winner UID, and podium rankings) to Firebase Firestore (`friend_challenges` collection and user profile challenge win counters `challengesWon`) as well as Personal Challenges to the user's cloud profile.
  - In the Challenges screen, make completed past challenges tappable to open a dedicated **Challenge Podium & Standings Sheet**:
    - **Podium Header**: Visually elevated 1st Place (Gold Crown/Trophy badge), 2nd Place (Silver Medal badge), and 3rd Place (Bronze Medal badge) cards showing each winner's name, focus time, and badge.
    - **Remaining Participants List**: All participants ranked 4th and below listed underneath with styled rank badges and their recorded focus duration.
  - On the Community Leaderboard (and Friends list), allow tapping any user (including yourself or any ranked member) to open their profile sheet displaying a prominent **Challenges Won** trophy badge with their total challenge victories count.

- **4. Interactive On-Screen Toast Banner for Friend "Return to Focus" Nudges**
  - When a friend in an active challenge sends a "Return to Focus" nudge, play the alert sound and vibration, post the high-priority notification, and immediately display an interactive on-screen toast banner (rendered over any screen when overlay permission is available and inside the app via a top floating banner).
  - The banner displays the sender's name, the "Return to Focus!" message, and a prominent **Return to Focus** action button that brings the user straight to the Focus timer and starts/resumes the Pomodoro.

- **5. Distinguish Session Break Cycle vs. Paused / Not Focusing in Challenge Status**
  - Add a dedicated `ON_BREAK` state to `StudyMemberTimerState` (alongside `FOCUSING`, `PAUSED`, and `IDLE`) synchronized in real time via Firestore.
  - When a participant's Pomodoro timer is in `SHORT_BREAK` or `LONG_BREAK` (running or transitioning in the break turn of the session cycle), broadcast `ON_BREAK` instead of `PAUSED` or `IDLE`.
  - Update the Study Group member rows, horloge status chips, and friend status badges (in English and French) to clearly distinguish:
    - **Focusing** (active focus turn)
    - **On Break / En pause (cycle)** (Pomodoro break turn of the session cycle, with a coffee/break badge)
    - **Paused / Not Focusing** (timer manually paused, auto-paused, or idle outside a session)
