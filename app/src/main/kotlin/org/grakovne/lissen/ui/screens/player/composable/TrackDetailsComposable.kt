package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.components.BookCoverKey

@Composable
fun BookCover(
  book: DetailedItem?,
  imageLoader: ImageLoader,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  val imageRequest =
    remember(book?.id) {
      ImageRequest
        .Builder(context)
        .data(book?.let { BookCoverKey(it.id) })
        .size(coil3.size.Size.ORIGINAL)
        .build()
    }

  AsyncShimmeringImage(
    imageRequest = imageRequest,
    imageLoader = imageLoader,
    contentDescription = null,
    contentScale = ContentScale.FillBounds,
    modifier = modifier.clip(RoundedCornerShape(8.dp)),
    error = painterResource(R.drawable.cover_fallback),
  )
}

fun provideChapterNumberTitle(
  currentTrackIndex: Int,
  book: DetailedItem?,
  libraryType: LibraryType,
  context: Context,
): String =
  when (libraryType) {
    LibraryType.LIBRARY -> {
      context.getString(
        R.string.player_screen_now_playing_title_chapter_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )
    }

    else -> {
      context.getString(
        R.string.player_screen_now_playing_title_item_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )
    }
  }
