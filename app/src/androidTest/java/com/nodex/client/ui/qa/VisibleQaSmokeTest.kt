package com.nodex.client.ui.qa

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nodex.client.MainActivity
import com.nodex.client.testing.NodexUiTestBootstrap
import com.nodex.client.testing.NodexUiTestConfig
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisibleQaSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoSmokeShowsMainTabs() {
        assumeScenario("demo")

        if (composeRule.onAllNodesWithTag("onboarding.logo").fetchSemanticsNodes().isNotEmpty()) {
            repeat(5) {
                composeRule.onNodeWithTag("onboarding.logo").performClick()
            }
        }

        assertTextExists("Overview")
        assertTextExists("Network")
        assertTextExists("Services")
        assertTextExists("Docker")
        assertTextExists("Settings")
    }

    @Test
    fun liveSmokeCreatesServerAndOpensOverview() {
        assumeScenario("live")

        val live = requireNotNull(loadLiveConfig())

        fillConnectionDetails(live)
        chooseAuthentication(live)
        composeRule.onNodeWithText("Test Connection").performScrollTo().performClick()

        trustHostKeyIfNeeded()

        composeRule.waitUntil(30_000) {
            hasText("Connection successful", substring = true)
        }

        composeRule.onNodeWithText("Continue").performScrollTo().performClick()

        assertTextExists("Overview")
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun bootstrap() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val read = { key: String -> runtimeArg(key) }

            if (!NodexUiTestBootstrap.isEnabled(read)) {
                return
            }

            if (NodexUiTestBootstrap.shouldResetPersistentState(read)) {
                NodexUiTestBootstrap.resetPersistentState(context)
            }

            if (NodexUiTestBootstrap.shouldUseDirectKeyImport(read)) {
                val keyFixture = NodexUiTestBootstrap.loadKeyFixture(read)
                if (keyFixture != null) {
                    NodexUiTestBootstrap.importKeyFixture(context, keyFixture)
                }
            }
        }
        @JvmStatic
        private fun runtimeArg(key: String): String? {
            return InstrumentationRegistry.getArguments().getString(key)?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun fillConnectionDetails(live: NodexUiTestConfig) {
        composeRule.onNodeWithTag("serverEditor.name").performTextReplacement(live.name)
        composeRule.onNodeWithTag("serverEditor.host").performTextReplacement(live.host)
        composeRule.onNodeWithTag("serverEditor.port").performTextReplacement(live.port)
        composeRule.onNodeWithTag("serverEditor.username").performTextReplacement(live.username)
    }

    private fun chooseAuthentication(live: NodexUiTestConfig) {
        val useImportedKey = live.keyText != null || live.keyPath != null
        val read = { key: String -> runtimeArg(key) }
        if (useImportedKey && NodexUiTestBootstrap.shouldUseDirectKeyImport(read)) {
            composeRule.onNodeWithText("SSH Key").performClick()
            composeRule.onNodeWithText("Choose from Library").assertIsEnabled().performClick()
            composeRule.onNodeWithTag("serverEditor.savedKey.${live.keyName}").performClick()
            return
        }

        assumeTrue(live.password != null)
        composeRule.onNodeWithTag("serverEditor.password").performTextReplacement(live.password.orEmpty())
    }

    private fun trustHostKeyIfNeeded() {
        composeRule.waitUntil(15_000) {
            hasText("Trust") || hasText("Connection successful", substring = true)
        }

        if (hasText("Trust")) {
            composeRule.onNodeWithText("Trust").performClick()
        }
    }

    private fun loadLiveConfig(): NodexUiTestConfig? {
        val read = { key: String -> runtimeArg(key) }
        return NodexUiTestBootstrap.loadConfig(read)
    }

    private fun assumeScenario(expected: String) {
        val scenario = runtimeArg("NODEX_UI_TEST_SCENARIO")?.lowercase()
        assumeTrue(scenario == expected || scenario == "both" || (scenario == null && expected == "demo"))
    }

    private fun assertTextExists(text: String) {
        composeRule.waitUntil(15_000) {
            hasText(text)
        }
    }

    private fun hasText(text: String, substring: Boolean = false): Boolean {
        return runCatching {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    }
}
