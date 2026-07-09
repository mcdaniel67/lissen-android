package org.grakovne.lissen.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.ImageRequest

/**
 * Renders a collection's artwork as a mosaic composed from the covers of the books it contains
 * (series, folder, author fallback). Backed by [SeriesCoverKey], which composes up to four covers
 * into a single square image; the caller sizes and clips via [modifier].
 */
@Composable
fun CollectionCoverImage(
  collectionId: String,
  coverItemIds: List<String>,
  imageLoader: ImageLoader,
  error: Painter,
  contentDescription: String?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  val imageRequest =
    remember(collectionId, coverItemIds) {
      ImageRequest
        .Builder(context)
        .data(SeriesCoverKey(collectionId, coverItemIds.take(MAX_COLLECTION_COVERS)))
        .build()
    }

  AsyncShimmeringImage(
    imageRequest = imageRequest,
    imageLoader = imageLoader,
    contentDescription = contentDescription,
    contentScale = ContentScale.Fit,
    modifier = modifier,
    error = error,
  )
}

private const val MAX_COLLECTION_COVERS = 4
