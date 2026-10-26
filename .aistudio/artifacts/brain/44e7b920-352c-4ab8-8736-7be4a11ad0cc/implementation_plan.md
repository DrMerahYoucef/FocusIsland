# Study Group UX/UI Redesign & Keyboard-Aware Chat Layout Plan

## Overview
We will redesign the **Study Group (`Groupe d'étude`)** screen in **Challenges (`Défis`)** so that it feels like a clean, modern real-time study messenger rather than a stack of nested cards, and ensure the chat stream remains spacious and readable when the soft keyboard (`IME`) opens.

---

## 1. Decluttering Chrome: Unified Compact Top Bar & Overflow Menu
Currently, six vertical layers of headers, sub-headers, and permanent bottom destructive buttons compete for screen height. We will consolidate them into a single **Unified Study Group Top Bar**:

- **Compact Group Header Bar**:
  - Displays the group icon/avatar stack, **Study Group title**, **Creator badge**, and live Horloge leader summary (`🕐 Vous (1h 40m) · youyou (1h 40m)`) in one sleek header surface.
  - Tapping the header/summary chip (`study_group_leaderboard_toggle`) smoothly expands or collapses the **Horloge Member Leaderboard** drawer (showing full/half/quarter clock icons, exact focus time, and 1-minute cooldown Nudge buttons).
  - Includes a compact **`+` Add Friends** button (`study_group_add_friends_button`) and a **`⋮` More Actions Menu** button right in the header.
- **Overflow Menu (`⋮`) for Secondary & Destructive Actions**:
  - Moves **Clear Chat** (`study_group_clear_chat_button`), **Leave / Abandon Group** (`surrender_friend_duel_button`), and creator-only **Delete Study Group** (`study_group_delete_group_button`) into the `⋮` dropdown menu—removing the permanent bottom button row (`Supprimer le défi` / `Abandonner`) that was stealing vertical space and sitting between the composer and the keyboard.
- **Remove Redundant Inner Sub-Header**:
  - Eliminates the duplicate `"Messages et emojis / Messages supprimés à la fin du défi..."` card header inside the chat container so the message list starts immediately below the compact top bar (and optional pending-invite pill bar).

---

## 2. Keyboard-Aware Layout & Direct Composer Docking
When the user taps the message input and the soft keyboard appears (`WindowInsets.isImeVisible`):

- **Eliminate the Dead Space Above the Keyboard**:
  - Currently, `MainShell`'s `Scaffold` applies ~80dp bottom padding for the `NavigationBar`, and `StudyGroupTabContent` adds `.imePadding()` on top of that—creating an 80dp empty gap above the keyboard and crushing the chat area.
  - We will hide the bottom `NavigationBar` in `MainShell` while the soft keyboard is visible on the Study Group screen and consume navigation bar insets cleanly so `.imePadding()` docks the message composer flush against the top of the keyboard.
- **Auto-Collapse Chrome on Keyboard Open**:
  - When `WindowInsets.isImeVisible` becomes `true`, the screen automatically collapses the expanded **Horloge Member Leaderboard**, hides the top app bar / tab row chrome while typing, and condenses the quick-emoji bar into a compact single-row strip or toggle so the **scrollable chat message stream retains generous vertical space** and auto-scrolls to the latest message.

---

## 3. Visual Layout Comparison (Idle vs. Keyboard Open)

```text
+--------------------------------------------------+      +--------------------------------------------------+
| Défis                                        [P] |      | [👥] Groupe d'étude · 🕐 Vous (1h40m) [▾] [+][⋮] |
| [ 🏳️ Défi personnel ]   [ 👥 Groupe d'étude ● ]  |      +--------------------------------------------------+
+--------------------------------------------------+      |                                                  |
| [👥] Défi Groupe d'étude [Créateur]   [+👤] [⋮]  |      |  [Y] youyou                                      |
| 🕐 Vous (1h 40m) · youyou (1h 40m)   Membres [▾] |      |      On attaque le chapitre 3 !           19:18  |
| ⏳ En attente: salmi (28m)                       |      |                                                  |
+--------------------------------------------------+      |                           Cc, j'arrive !  19:19  |
|                                                  |      |                                              [V] |
|  [Y] youyou                                      |      |     🔔 youyou a relancé salmi · 19:19            |
|      On attaque le chapitre 3 !           19:18  |      |                                                  |
|                                                  |      +--------------------------------------------------+
|                           Cc, j'arrive !  19:19  |      |  🔥   💪   📚   🎯   ☕   👏   ⚡   🏆            |
|                                              [V] |      | [ Écrire au groupe d'étude...            ] [ ➤ ] |
|     🔔 youyou a relancé salmi · 19:19            |      +==================================================+
|                                                  |      |              SOFT KEYBOARD (IME)                 |
+--------------------------------------------------+      |      (Docked flush — zero dead space gap)        |
|  🔥   💪   📚   🎯   ☕   👏   ⚡   🏆            |      |                                                  |
| [ Écrire au groupe d'étude...            ] [ ➤ ] |      |                                                  |
+--------------------------------------------------+      +==================================================+
|  ⏱️ Focus  🎴 Étude  🏳️ Défis  📈 Progrès  👥 Com |
+--------------------------------------------------+
          1. Standard View (Spacious Chat)                     2. Keyboard Open (Auto-Collapsed & Docked)
```
