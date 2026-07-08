package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.BookDownloadState
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.DownloadStateIcon
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.icons.TimerPlay
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel
import java.util.Locale

@Composable
fun NavigationBarComposable(
  book: DetailedItem,
  playerViewModel: PlayerViewModel,
  contentCachingModelView: CachingModelView,
  navController: AppNavigationService,
  modifier: Modifier = Modifier,
  libraryType: LibraryType,
) {
  val downloadState by contentCachingModelView
    .downloadState(book.id)
    .collectAsState(initial = BookDownloadState.NotDownloaded)
  val timerOption by playerViewModel.timerOption.collectAsState()
  val timerRemaining by playerViewModel.timerRemaining.collectAsState()
  val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
  val hasEpisodes = book.chapters.isNotEmpty()
  val isForceCache = contentCachingModelView.localCacheUsing()

  var playbackSpeedExpanded by remember { mutableStateOf(false) }
  var timerExpanded by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf(false) }

  val scope = rememberCoroutineScope()

  val downloadActive = downloadState !is BookDownloadState.NotDownloaded
  val speedActive = playbackSpeed != 1.0f
  val timerActive = timerOption != null

  Surface(
    shadowElevation = 4.dp,
    modifier = modifier.height(64.dp),
  ) {
    NavigationBar(
      containerColor = Color.Transparent,
      contentColor = colorScheme.onBackground,
      modifier = Modifier.fillMaxWidth(),
    ) {
      val iconSize = 24.dp
      val labelStyle = typography.labelSmall.copy(fontSize = 10.sp)

      NavigationBarItem(
        icon = {
          DownloadStateIcon(
            downloadState = downloadState,
            size = iconSize,
          )
        },
        label = {
          Text(
            text = stringResource(R.string.player_screen_downloads_navigation),
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        // Offline (force-cache) mode disables starting a new download, mirroring how the old
        // sheet greyed out its download options — but stopping or deleting stays available.
        enabled = hasEpisodes && !(downloadState is BookDownloadState.NotDownloaded && isForceCache),
        selected = false,
        onClick = {
          when (downloadState) {
            is BookDownloadState.NotDownloaded -> {
              contentCachingModelView.cache(
                mediaItem = book,
                currentPosition = playerViewModel.totalPosition.value,
                option = AllItemsDownloadOption,
              )
            }

            is BookDownloadState.Downloading -> {
              contentCachingModelView.stopCaching(book)
            }

            is BookDownloadState.Downloaded -> {
              showDeleteDialog = true
            }
          }
        },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
            unselectedTextColor = if (downloadActive) colorScheme.primary else colorScheme.onSurfaceVariant,
          ),
      )

      NavigationBarItem(
        enabled = hasEpisodes,
        icon = {
          Icon(
            Icons.Outlined.SlowMotionVideo,
            contentDescription = stringResource(R.string.player_screen_playback_speed_navigation),
            modifier = Modifier.size(iconSize),
          )
        },
        label = {
          Text(
            text =
              when (speedActive) {
                true -> "${String.format(Locale.US, "%.2f", playbackSpeed).trimEnd('0').trimEnd('.')}×"
                false -> stringResource(R.string.player_screen_playback_speed_navigation)
              },
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        selected = false,
        onClick = { playbackSpeedExpanded = true },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
            unselectedIconColor = if (speedActive) colorScheme.primary else colorScheme.onSurfaceVariant,
            unselectedTextColor = if (speedActive) colorScheme.primary else colorScheme.onSurfaceVariant,
          ),
      )

      NavigationBarItem(
        icon = {
          Icon(
            when (timerOption) {
              null -> Icons.Outlined.Timer
              else -> TimerPlay
            },
            contentDescription = stringResource(R.string.player_screen_timer_navigation),
            modifier = Modifier.size(iconSize),
          )
        },
        label = {
          when (timerOption) {
            is DurationTimerOption, CurrentEpisodeTimerOption -> {
              Text(
                text =
                  timerRemaining
                    ?.toInt()
                    ?.formatTime(false)
                    ?: stringResource(R.string.player_screen_timer_navigation),
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            null -> {
              Text(
                text = stringResource(R.string.player_screen_timer_navigation),
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        },
        enabled = hasEpisodes,
        selected = false,
        onClick = { timerExpanded = true },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
            unselectedIconColor = if (timerActive) colorScheme.primary else colorScheme.onSurfaceVariant,
            unselectedTextColor = if (timerActive) colorScheme.primary else colorScheme.onSurfaceVariant,
          ),
      )

      if (playbackSpeedExpanded) {
        PlaybackSpeedComposable(
          currentSpeed = playbackSpeed,
          onSpeedChange = { playerViewModel.setPlaybackSpeed(it) },
          onDismissRequest = { playbackSpeedExpanded = false },
        )
      }

      if (timerExpanded) {
        TimerComposable(
          libraryType = libraryType,
          currentOption = timerOption,
          onOptionSelected = { playerViewModel.setTimer(it) },
          onDismissRequest = { timerExpanded = false },
        )
      }

      if (showDeleteDialog) {
        AlertDialog(
          onDismissRequest = { showDeleteDialog = false },
          title = { Text(stringResource(R.string.player_screen_download_delete_title)) },
          text = { Text(stringResource(R.string.player_screen_download_delete_message)) },
          confirmButton = {
            TextButton(onClick = {
              showDeleteDialog = false
              scope.launch {
                contentCachingModelView.dropCache(book.id)
                playerViewModel.clearPlayingBook()
                navController.showLibrary(true)
              }
            }) {
              Text(stringResource(R.string.player_screen_download_delete_confirm))
            }
          },
          dismissButton = {
            TextButton(onClick = { showDeleteDialog = false }) {
              Text(stringResource(R.string.library_folder_dialog_cancel))
            }
          },
        )
      }
    }
  }
}
