package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryTabsComposableTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun allAudiobooksIsSelectedWhenDownloadsOnlyIsFalse() {
    composeRule.setContent {
      MaterialTheme {
        LibraryTabsComposable(downloadsOnly = false, onDownloadsOnlyChanged = {})
      }
    }

    composeRule.onNodeWithTag("libraryTabAll").assertIsSelected()
    composeRule.onNodeWithTag("libraryTabDownloads").assertIsNotSelected()
  }

  @Test
  fun clickingDownloadsReportsAndSelectsDownloads() {
    val received = mutableListOf<Boolean>()

    composeRule.setContent {
      var downloadsOnly by remember { mutableStateOf(false) }
      MaterialTheme {
        LibraryTabsComposable(
          downloadsOnly = downloadsOnly,
          onDownloadsOnlyChanged = {
            downloadsOnly = it
            received += it
          },
        )
      }
    }

    composeRule.onNodeWithTag("libraryTabDownloads").performClick()
    composeRule.onNodeWithTag("libraryTabDownloads").assertIsSelected()
    composeRule.onNodeWithTag("libraryTabAll").assertIsNotSelected()
    assertEquals(listOf(true), received)
  }

  @Test
  fun clickingAllReportsAndSelectsAllAudiobooks() {
    val received = mutableListOf<Boolean>()

    composeRule.setContent {
      var downloadsOnly by remember { mutableStateOf(true) }
      MaterialTheme {
        LibraryTabsComposable(
          downloadsOnly = downloadsOnly,
          onDownloadsOnlyChanged = {
            downloadsOnly = it
            received += it
          },
        )
      }
    }

    composeRule.onNodeWithTag("libraryTabAll").performClick()
    composeRule.onNodeWithTag("libraryTabAll").assertIsSelected()
    composeRule.onNodeWithTag("libraryTabDownloads").assertIsNotSelected()
    assertEquals(listOf(false), received)
  }
}
