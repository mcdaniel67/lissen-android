package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.grakovne.lissen.R

@Composable
fun LibraryTabsComposable(
  downloadsOnly: Boolean,
  onDownloadsOnlyChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  PrimaryTabRow(
    selectedTabIndex = if (downloadsOnly) 1 else 0,
    modifier = modifier.testTag("libraryTabs"),
  ) {
    Tab(
      selected = !downloadsOnly,
      onClick = { onDownloadsOnlyChanged(false) },
      text = {
        Text(
          text = stringResource(R.string.library_tab_all_audiobooks),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      modifier = Modifier.testTag("libraryTabAll"),
    )
    Tab(
      selected = downloadsOnly,
      onClick = { onDownloadsOnlyChanged(true) },
      text = {
        Text(
          text = stringResource(R.string.library_tab_downloads),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      },
      modifier = Modifier.testTag("libraryTabDownloads"),
    )
  }
}
