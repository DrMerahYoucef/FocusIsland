package com.example

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.challenge.ChallengeResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = FocusFlowApplication::class)
class MainActivityStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullAppFlowAndTabsAndSettingsAndDialogsDoNotCrash() {
        // 1. Verify "Continue without an account" option is removed from AuthScreen
        val guestNodes = composeRule.onAllNodes(hasText("Continue without an account", substring = true))
        org.junit.Assert.assertTrue(guestNodes.fetchSemanticsNodes().isEmpty())

        // Enter app for test via in-memory auth session and ensure clean challenge state
        composeRule.activityRule.scenario.onActivity {
            FocusFlowApplication.instance.authRepository.continueAsGuest()
            val store = FocusFlowApplication.instance.challengeStore
            if (store.state.value.active != null) {
                store.finish(ChallengeResult.DONE)
            }
            store.clearHistory()
        }
        composeRule.waitForIdle()

        // 2. Navigate to Challenges tab
        composeRule.onNodeWithText("Challenges").performClick()
        composeRule.waitForIdle()

        // Ensure Personal tab is selected before opening New Challenge bottom sheet
        val personalTabNodes = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("challenges_tab_personal"))
        if (personalTabNodes.fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("challenges_tab_personal").performClick()
            composeRule.waitForIdle()
        }

        // 3. Open New Challenge bottom sheet and verify Lock Phone Permitted Apps selector
        composeRule.onNodeWithText("New challenge").performScrollTo()
        composeRule.onNodeWithText("New challenge").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("personal_mode_lock_phone").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_phone_permitted_apps_card").performScrollTo()
        composeRule.onNodeWithTag("lock_phone_choose_apps_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("permitted_picker_save_button").performClick()
        composeRule.waitForIdle()

        // 4. Close sheet and navigate to Study, Progress, Community tabs
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Study").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Progress").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Community").performClick()
        composeRule.waitForIdle()

        // 5. Return to Focus tab where the Settings button lives, open Settings and switch language
        composeRule.onNodeWithText("Focus").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Français").performScrollTo()
        composeRule.onNodeWithText("Français").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("English").performScrollTo()
        composeRule.onNodeWithText("English").performClick()
        composeRule.waitForIdle()
    }
}
