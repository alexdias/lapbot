package com.example.lapbot.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.lapbot.data.TimingUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainScreen(
        state = TimingUiState(),
        onConnect = {},
        onDisconnect = {},
        onAutoReconnectChange = {},
        onTailLimitChange = {},
        onReconnectPolicyChange = {},
        onDriverClick = {},
        onAnnouncementsClick = {},
      )
    }
  }

  @Test
  fun disconnectedControlsAreShown() {
    composeTestRule.onNodeWithText("Connect").assertExists()
    composeTestRule.onNodeWithText("Auto-reconnect").assertExists()
    composeTestRule.onNodeWithText("Announcer").assertExists()
    composeTestRule.onNodeWithText("Current timing").assertExists()
    composeTestRule.onNodeWithText("JSON stream tail").assertExists()
  }
}
