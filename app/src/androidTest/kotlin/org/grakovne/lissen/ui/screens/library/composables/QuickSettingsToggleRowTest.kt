package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuickSettingsToggleRowTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun rowExposesOneToggleableNodeAndUpdatesCheckedState() {
    var clicks = 0
    composeRule.setContent {
      var checked by remember { mutableStateOf(false) }
      MaterialTheme {
        ToggleRow(
          title = "Downloaded first",
          icon = Icons.Outlined.DownloadForOffline,
          checked = checked,
          onClick = {
            checked = !checked
            clicks++
          },
        )
      }
    }

    assertEquals(1, composeRule.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
    composeRule
      .onNode(isToggleable())
      .assertIsEnabled()
      .assertIsOff()
      .performClick()
      .assertIsOn()
    assertEquals(1, clicks)
  }

  @Test
  fun disabledRowExposesDisabledSemanticsAndDoesNotClick() {
    var clicks = 0
    composeRule.setContent {
      MaterialTheme {
        ToggleRow(
          title = "Downloaded first",
          icon = Icons.Outlined.DownloadForOffline,
          checked = true,
          enabled = false,
          onClick = { clicks++ },
        )
      }
    }

    composeRule
      .onNode(isToggleable())
      .assertIsNotEnabled()
      .assertIsOn()
      .performClick()
    assertEquals(0, clicks)
  }
}
