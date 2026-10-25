package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = FocusFlowApplication::class)
class MainActivityStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullAppFlowAndTabsAndSettingsAndDialogsDoNotCrash() {
        composeRule.waitForIdle()

        // 1. Enter app as guest if on AuthScreen
        val guestNodes = composeRule.onAllNodes(androidx.compose.ui.test.hasText("Continue without an account", substring = true))
        if (guestNodes.fetchSemanticsNodes().isNotEmpty()) {
            guestNodes[0].performScrollTo()
            guestNodes[0].performClick()
            composeRule.waitForIdle()
        }

        // 2. Navigate to Challenges tab
        composeRule.onNodeWithText("Challenges").performClick()
        composeRule.waitForIdle()

        // 3. Open New Challenge bottom sheet
        composeRule.onNodeWithText("New challenge").performClick()
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

        // 5. Open Settings and switch language to French and English
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
