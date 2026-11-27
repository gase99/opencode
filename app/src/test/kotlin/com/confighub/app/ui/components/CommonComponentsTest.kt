package com.confighub.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.confighub.app.core.model.Protocol
import com.confighub.app.ui.theme.ConfigHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CommonComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun protocolBadgeShowsProtocolName() {
        composeRule.setContent {
            ConfigHubTheme {
                ProtocolBadge(Protocol.VMESS)
            }
        }
        composeRule.onNodeWithText("VMESS").assertExists()
    }

    @Test
    fun emptyStateRendersTitleAndBody() {
        composeRule.setContent {
            ConfigHubTheme {
                EmptyState(title = "Empty title", body = "Empty body")
            }
        }
        composeRule.onNodeWithText("Empty title").assertExists()
        composeRule.onNodeWithText("Empty body").assertExists()
    }

    @Test
    fun scoreBadgeShowsDashWhenNull() {
        composeRule.setContent {
            ConfigHubTheme {
                ScoreBadge(null)
            }
        }
        composeRule.onNodeWithText("–").assertExists()
    }
}
