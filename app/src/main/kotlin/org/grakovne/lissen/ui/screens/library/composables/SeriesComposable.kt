package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import kotlinx.coroutines.delay
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.seriesSequence
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookDownloadState
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.ui.components.CollectionCoverImage
import org.grakovne.lissen.ui.components.DownloadStateIcon
import org.grakovne.lissen.ui.navigation.AppNavigationService

private const val SERIES_PREFETCH_DWELL_MS = 200L

@Composable
fun SeriesComposable(
  series: LibraryEntry.SeriesEntry,
  expanded: Boolean,
  loading: Boolean,
  books: List<Book>,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  onToggle: () -> Unit,
  onPrefetch: () -> Unit,
  resolveDownloadState: (String) -> BookDownloadState = { BookDownloadState.NotDownloaded },
) {
  val context = LocalContext.current

  LaunchedEffect(series.id) {
    delay(SERIES_PREFETCH_DWELL_MS)
    onPrefetch()
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button) { onToggle() }
          .testTag("seriesItem_${series.id}")
          .padding(horizontal = 4.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CollectionCoverImage(
        collectionId = series.id,
        coverItemIds = series.coverItemIds,
        imageLoader = imageLoader,
        error = painterResource(R.drawable.cover_fallback),
        contentDescription = null,
        modifier =
          Modifier
            .size(LibraryItemCoverSize)
            .clip(RoundedCornerShape(4.dp)),
      )

      Spacer(Modifier.width(16.dp))

      Column(Modifier.weight(1f)) {
        Text(
          text = series.title,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onBackground,
            ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        series.author
          ?.takeIf { it.isNotBlank() }
          ?.let {
            Text(
              text = it,
              style =
                MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

        Text(
          text = context.resources.getQuantityString(R.plurals.series_books_count, series.bookCount, series.bookCount),
          style =
            MaterialTheme.typography.bodyMedium.copy(
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      val allDownloaded =
        books.isNotEmpty() && books.all { resolveDownloadState(it.id) == BookDownloadState.Downloaded }

      if (allDownloaded) {
        Spacer(Modifier.width(12.dp))
        DownloadStateIcon(
          downloadState = BookDownloadState.Downloaded,
          size = 20.dp,
          contentDescription = stringResource(R.string.library_item_downloaded_indicator),
          modifier = Modifier.testTag("downloadedIndicator_${series.id}"),
        )
      }

      Spacer(Modifier.width(16.dp))

      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
      )
    }

    AnimatedVisibility(visible = expanded) {
      Column(modifier = Modifier.fillMaxWidth()) {
        when {
          loading && books.isEmpty() -> {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          else -> {
            val sequences = books.mapIndexed { index, book -> book.seriesSequence() ?: "${index + 1}" }
            val widthReserve = "0".repeat(sequences.maxOfOrNull { it.length } ?: 1)

            books.forEachIndexed { index, book ->
              BookComposable(
                book = book,
                imageLoader = imageLoader,
                navController = navController,
                grouping = LibraryGrouping.SERIES,
                downloadState = resolveDownloadState(book.id),
                leading = { SeriesSequenceLabel(number = sequences[index], widthReserve = widthReserve) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SeriesSequenceLabel(
  number: String,
  widthReserve: String,
) {
  val style =
    MaterialTheme.typography.bodyMedium.copy(
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )

  Box(
    modifier = Modifier.padding(end = 10.dp),
    contentAlignment = Alignment.CenterEnd,
  ) {
    Text(text = widthReserve, style = style, maxLines = 1, modifier = Modifier.alpha(0f))
    Text(text = number, style = style, maxLines = 1)
  }
}
