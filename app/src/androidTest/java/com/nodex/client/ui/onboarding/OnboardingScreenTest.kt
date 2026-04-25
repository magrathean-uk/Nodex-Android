package com.nodex.client.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.nodex.client.ui.screens.onboarding.OnboardingContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingIconFiveTimesEntersDemo() {
        var demoTriggered = false

        composeRule.setContent {
            OnboardingContent(
                onComplete = {},
                onDemo = { demoTriggered = true }
            )
        }

        waitForNode("onboarding.logo")

        repeat(5) {
            composeRule.onNodeWithTag("onboarding.logo").performClick()
        }

        assertTrue("Demo should trigger after 5 taps", demoTriggered)
    }

    @Test
    fun onboardingCompletesAfterEnteringConnectionDetails() {
        var completed = false

        composeRule.setContent {
            OnboardingContent(
                onComplete = { completed = true },
                onDemo = {}
            )
        }

        waitForNode("serverEditor.host")
        composeRule.onNodeWithTag("serverEditor.host").performTextInput("demo.local")
        composeRule.onNodeWithTag("serverEditor.password").performTextInput("secret")
        composeRule.onNodeWithTag("serverEditor.save").performScrollTo().performClick()

        assertTrue("Onboarding should complete after valid setup", completed)
    }

    private fun waitForNode(tag: String) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }
}
