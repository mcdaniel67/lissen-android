package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.android.awaitFrame
import org.grakovne.lissen.ui.components.withScrollbar
import org.grakovne.lissen.ui.screens.player.composable.common.provideNowPlayingTitle
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun PlayingQueueComposable(
  libraryViewModel: LibraryViewModel,
  cachingModelView: CachingModelView,
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  val book by viewModel.book.collectAsState()
  val searchToken by viewModel.searchToken.collectAsState()

  val showingChapters by remember {
    derivedStateOf {
      when (searchToken.isEmpty()) {
        true -> {
          book
            ?.chapters
            ?: emptyList()
        }

        false -> {
          book
            ?.chapters
            ?.filter { it.title.lowercase().contains(searchToken.lowercase()) }
            ?: emptyList()
        }
      }
    }
  }

  val currentTrackIndex by viewModel.currentChapterIndex.collectAsState()
  val currentTrackId by remember {
    derivedStateOf {
      book?.chapters?.getOrNull(currentTrackIndex)
    }
  }

  val playbackReady by viewModel.isPlaybackReady.collectAsState()

  val listState = rememberLazyListState()

  val showScrollbar by remember {
    derivedStateOf {
      listState.isScrollInProgress
    }
  }

  val scrollbarAlpha by animateFloatAsState(
    targetValue = if (showScrollbar) 1f else 0f,
    animationSpec = tween(durationMillis = 300),
  )

  LaunchedEffect(currentTrackIndex) {
    awaitFrame()
    scrollPlayingQueue(
      currentTrackIndex = currentTrackIndex,
      listState = listState,
      playbackReady = playbackReady,
    )
  }

  Column(
    modifier =
      modifier
        .testTag("chapterList")
        .fillMaxSize()
        .withScrollbar(
          state = listState,
          color = colorScheme.onBackground.copy(alpha = scrollbarAlpha),
          totalItems = showingChapters.size,
          ignoreItems = emptyList(),
        ).padding(horizontal = 16.dp),
  ) {
    Text(
      text = provideNowPlayingTitle(libraryViewModel.fetchPreferredLibraryType(), context),
      fontSize = typography.titleMedium.fontSize * 1.25f,
      fontWeight = FontWeight.SemiBold,
      color = colorScheme.primary,
      modifier = Modifier.padding(horizontal = 6.dp),
    )

    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
      contentPadding = PaddingValues(bottom = 12.dp),
      modifier = Modifier.fillMaxHeight(),
      state = listState,
    ) {
      val maxDuration = showingChapters.maxOfOrNull { it.duration } ?: 0.0

      itemsIndexed(
        showingChapters,
        key = { _, chapter -> chapter.id },
      ) { index, chapter ->
        val bookId = book?.id ?: ""

        val cacheStateFlow =
          remember(bookId, chapter.id) {
            cachingModelView.provideCacheState(bookId = bookId, chapterId = chapter.id)
          }
        val isCached by cacheStateFlow.collectAsState(initial = false)

        PlaylistItemComposable(
          track = chapter,
          onClick = { viewModel.setChapter(chapter) },
          isSelected = chapter.id == currentTrackId?.id,
          modifier = Modifier.wrapContentWidth(),
          maxDuration = maxDuration,
          isCached = isCached,
        )

        if (index < showingChapters.size - 1) {
          HorizontalDivider(
            thickness = 1.dp,
            modifier =
              Modifier
                .padding(start = 24.dp)
                .padding(vertical = 8.dp),
          )
        }
      }
    }
  }
}

/**
 * Auto-scrolls the chapter list so the current chapter is centered.
 *
 * The list is now the only scrollable surface on the player, so the user drives it directly.
 * To avoid fighting an in-progress user scroll, we skip auto-scrolling while the list is being
 * dragged/flung: a chapter change that lands during an active user scroll simply won't re-center
 * (the next chapter change, or the scroll settling, resolves it). Taps on a chapter row are not
 * "scroll in progress", so tapping a chapter still recenters as expected.
 */
private suspend fun scrollPlayingQueue(
  currentTrackIndex: Int,
  listState: LazyListState,
  playbackReady: Boolean,
) {
  if (listState.isScrollInProgress) {
    return
  }

  val targetIndex = currentTrackIndex.coerceAtLeast(0)

  val layoutInfo = listState.layoutInfo
  val viewportHeight = layoutInfo.viewportSize.height
  val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
  val centeringOffset =
    when {
      viewportHeight > 0 && itemHeight in 1 until viewportHeight -> -((viewportHeight - itemHeight) / 2)
      else -> 0
    }

  when (playbackReady) {
    true -> listState.animateScrollToItem(targetIndex, centeringOffset)
    false -> listState.scrollToItem(targetIndex, centeringOffset)
  }
}
