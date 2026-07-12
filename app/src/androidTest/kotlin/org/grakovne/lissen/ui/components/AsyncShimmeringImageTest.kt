package org.grakovne.lissen.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import kotlinx.coroutines.CompletableDeferred
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import java.io.File

class AsyncShimmeringImageTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun reportsLoadingImmediatelyWithoutShowingShimmer() {
    val gate = CompletableDeferred<Unit>()
    val states = mutableListOf<Boolean>()
    val request = request(ControlledImage("cover"))
    val imageLoader = imageLoader(mapOf("cover" to gate))

    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      AsyncShimmeringImage(
        imageRequest = request,
        imageLoader = imageLoader,
        contentDescription = "book cover",
        contentScale = ContentScale.Fit,
        error = ColorPainter(Color.Gray),
        onLoadingStateChanged = { states += it },
      )
    }

    composeRule.onNodeWithContentDescription("book cover").assertExists()
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()
    composeRule.runOnIdle {
      assert(states.firstOrNull() == true) { "expected initial loading=true, got $states" }
    }
  }

  @Test
  fun showsShimmerOnlyAfterDelayAndHidesItOnSuccess() {
    val gate = CompletableDeferred<Unit>()
    val states = mutableListOf<Boolean>()
    val request = request(ControlledImage("cover"))
    val imageLoader = imageLoader(mapOf("cover" to gate))

    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      AsyncShimmeringImage(
        imageRequest = request,
        imageLoader = imageLoader,
        contentDescription = "book cover",
        contentScale = ContentScale.Fit,
        error = ColorPainter(Color.Gray),
        onLoadingStateChanged = { states += it },
      )
    }

    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()
    composeRule.mainClock.advanceTimeBy(100)
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()
    composeRule.mainClock.advanceTimeBy(60)
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertExists()

    gate.complete(Unit)
    composeRule.mainClock.autoAdvance = true
    composeRule.waitUntil(timeoutMillis = 5_000) { states.lastOrNull() == false }
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()
  }

  @Test
  fun changingRequestRestartsTheShimmerDelay() {
    val firstGate = CompletableDeferred<Unit>()
    val secondGate = CompletableDeferred<Unit>()
    val states = mutableListOf<Boolean>()
    var image by mutableStateOf(ControlledImage("first"))
    val imageLoader = imageLoader(mapOf("first" to firstGate, "second" to secondGate))

    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      val imageRequest = remember(image) { request(image) }
      AsyncShimmeringImage(
        imageRequest = imageRequest,
        imageLoader = imageLoader,
        contentDescription = "book cover",
        contentScale = ContentScale.Fit,
        error = ColorPainter(Color.Gray),
        onLoadingStateChanged = { states += it },
      )
    }

    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()
    composeRule.runOnIdle {
      assert(states.firstOrNull() == true) { "expected initial loading=true, got $states" }
    }
    composeRule.mainClock.advanceTimeBy(ASYNC_IMAGE_SHIMMER_DELAY_MS - 1)
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()
    composeRule.mainClock.advanceTimeBy(2)
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertExists()

    composeRule.runOnIdle { image = ControlledImage("second") }
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()

    composeRule.mainClock.advanceTimeBy(100)
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertDoesNotExist()
    composeRule.mainClock.advanceTimeBy(60)
    composeRule.onNodeWithTag(ASYNC_IMAGE_SHIMMER_TEST_TAG).assertExists()
  }

  private fun request(data: ControlledImage) = ImageRequest.Builder(context).data(data).build()

  private fun imageLoader(gates: Map<String, CompletableDeferred<Unit>>): ImageLoader =
    ImageLoader
      .Builder(context)
      .components {
        add(ControlledImageFetcherFactory(gates, imageFile()))
      }.build()

  private fun imageFile(): File =
    File(context.cacheDir, "async-shimmering-image-test.png").also { file ->
      if (!file.exists()) {
        Bitmap
          .createBitmap(1, 1, Bitmap.Config.ARGB_8888)
          .also { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
          }
      }
    }
}

private data class ControlledImage(
  val key: String,
)

private class ControlledImageFetcherFactory(
  private val gates: Map<String, CompletableDeferred<Unit>>,
  private val image: File,
) : Fetcher.Factory<ControlledImage> {
  override fun create(
    data: ControlledImage,
    options: Options,
    imageLoader: ImageLoader,
  ): Fetcher = ControlledImageFetcher(checkNotNull(gates[data.key]), image)
}

private class ControlledImageFetcher(
  private val gate: CompletableDeferred<Unit>,
  private val image: File,
) : Fetcher {
  override suspend fun fetch(): FetchResult {
    gate.await()
    return SourceFetchResult(
      source =
        ImageSource(
          file = image.toOkioPath(),
          fileSystem = FileSystem.SYSTEM,
        ),
      mimeType = "image/png",
      dataSource = DataSource.DISK,
    )
  }
}
