package org.grakovne.lissen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.delay

const val ASYNC_IMAGE_SHIMMER_TEST_TAG = "asyncImageShimmer"

internal const val ASYNC_IMAGE_SHIMMER_DELAY_MS = 150L

@Composable
fun AsyncShimmeringImage(
  imageRequest: ImageRequest,
  imageLoader: ImageLoader,
  contentDescription: String?,
  contentScale: ContentScale,
  modifier: Modifier = Modifier,
  error: Painter,
  onLoadingStateChanged: (Boolean) -> Unit = {},
) {
  var isLoading by remember(imageRequest) { mutableStateOf(true) }
  var showShimmer by remember(imageRequest) { mutableStateOf(false) }
  val currentOnLoadingStateChanged by rememberUpdatedState(onLoadingStateChanged)

  LaunchedEffect(imageRequest, isLoading) {
    currentOnLoadingStateChanged(isLoading)
    showShimmer = false

    if (isLoading) {
      delay(ASYNC_IMAGE_SHIMMER_DELAY_MS)
      showShimmer = true
    }
  }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    if (showShimmer) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .shimmer()
            .background(Color.Gray)
            .testTag(ASYNC_IMAGE_SHIMMER_TEST_TAG),
      )
    }

    AsyncImage(
      model = imageRequest,
      imageLoader = imageLoader,
      contentDescription = contentDescription,
      contentScale = contentScale,
      modifier = Modifier.fillMaxSize(),
      onLoading = {
        isLoading = true
      },
      onSuccess = {
        isLoading = false
      },
      onError = {
        isLoading = false
      },
      error = error,
    )
  }
}
