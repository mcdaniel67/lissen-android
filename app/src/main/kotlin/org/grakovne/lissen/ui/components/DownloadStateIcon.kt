package org.grakovne.lissen.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.BookDownloadState

@Composable
fun DownloadStateIcon(
  downloadState: BookDownloadState,
  size: Dp,
  modifier: Modifier = Modifier,
) {
  when (downloadState) {
    is BookDownloadState.Downloading -> {
      val iconSize = size - 2.dp
      CircularProgressIndicator(
        progress = { downloadState.progress.coerceIn(0.0, 1.0).toFloat() },
        modifier = modifier.size(iconSize),
        strokeWidth = iconSize * 0.1f,
        color = colorScheme.primary,
        trackColor = LocalContentColor.current,
        strokeCap = StrokeCap.Butt,
        gapSize = 2.dp,
      )
    }

    is BookDownloadState.Downloaded -> {
      Icon(
        imageVector = Icons.Filled.CloudDone,
        contentDescription = stringResource(R.string.player_screen_downloads_navigation),
        tint = colorScheme.primary,
        modifier = modifier.size(size),
      )
    }

    is BookDownloadState.NotDownloaded -> {
      Icon(
        imageVector = Icons.Outlined.CloudDownload,
        contentDescription = stringResource(R.string.player_screen_downloads_navigation),
        modifier = modifier.size(size),
      )
    }
  }
}
