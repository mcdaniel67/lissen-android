package org.grakovne.lissen.content.cache.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SeriesCoverComposerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val composer = SeriesCoverComposer()

  @Test
  fun threeCoversFillASquareWithAFullHeightLeftColumnAndTwoStackedOnTheRight() {
    val red = solidCoverFile("red.png", Color.RED)
    val green = solidCoverFile("green.png", Color.GREEN)
    val blue = solidCoverFile("blue.png", Color.BLUE)

    val composed = composer.compose(listOf(red, green, blue))
    assertNotNull(composed)

    val result = BitmapFactory.decodeStream(composed!!.inputStream())

    assertEquals(100, result.width)
    assertEquals(100, result.height)

    assertEquals("left column is the first cover", Color.RED, result.getPixel(25, 50))
    assertEquals("right-top is the second cover", Color.GREEN, result.getPixel(75, 25))
    assertEquals("right-bottom is the third cover", Color.BLUE, result.getPixel(75, 75))
  }

  @Test
  fun fourCoversTileIntoA2x2Grid() {
    val red = solidCoverFile("red.png", Color.RED)
    val green = solidCoverFile("green.png", Color.GREEN)
    val blue = solidCoverFile("blue.png", Color.BLUE)
    val yellow = solidCoverFile("yellow.png", Color.YELLOW)

    val composed = composer.compose(listOf(red, green, blue, yellow))
    assertNotNull(composed)

    val result = BitmapFactory.decodeStream(composed!!.inputStream())

    assertEquals(100, result.width)
    assertEquals(100, result.height)

    assertEquals("top-left quadrant", Color.RED, result.getPixel(25, 25))
    assertEquals("top-right quadrant", Color.GREEN, result.getPixel(75, 25))
    assertEquals("bottom-left quadrant", Color.BLUE, result.getPixel(25, 75))
    assertEquals("bottom-right quadrant", Color.YELLOW, result.getPixel(75, 75))
  }

  @Test
  fun caps_at_four_tiles_ignoring_extra_covers() {
    val covers = (1..6).map { solidCoverFile("cover-$it.png", Color.RED) }

    val composed = composer.compose(covers)
    assertNotNull(composed)

    val result = BitmapFactory.decodeStream(composed!!.inputStream())
    assertEquals(100, result.width)
    assertEquals(100, result.height)
  }

  private fun solidCoverFile(
    name: String,
    color: Int,
  ): File {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(color)

    val file = File(context.cacheDir, name)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
  }
}
