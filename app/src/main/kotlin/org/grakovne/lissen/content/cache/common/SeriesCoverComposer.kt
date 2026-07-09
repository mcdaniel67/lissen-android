package org.grakovne.lissen.content.cache.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import okio.Buffer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Composes up to [MAX_TILES] book covers into a single square mosaic used as the artwork for a
 * collection (series, folder, author fallback). Fewer than four covers still fill the square with a
 * tidy layout so a collection never renders with empty gaps.
 */
@Singleton
class SeriesCoverComposer
  @Inject
  constructor() {
    fun compose(covers: List<File>): Buffer? {
      val bitmaps =
        covers
          .take(MAX_TILES)
          .mapNotNull { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

      if (bitmaps.isEmpty()) {
        return null
      }

      val canvasSize = bitmaps.maxOf { max(it.width, it.height) }.coerceAtLeast(1)
      val result = createBitmap(canvasSize, canvasSize)
      val canvas = Canvas(result)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

      cellRects(bitmaps.size, canvasSize.toFloat())
        .forEachIndexed { index, rect -> canvas.drawCenterCrop(bitmaps[index], rect, paint) }

      bitmaps.forEach { it.recycle() }

      return Buffer().also { buffer ->
        result.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
        result.recycle()
      }
    }

    /**
     * Cell rectangles for [count] covers on a [size]×[size] canvas: 1 → full, 2 → side-by-side
     * halves, 3 → a full-height left column plus two stacked on the right, 4 → a 2×2 grid.
     */
    private fun cellRects(
      count: Int,
      size: Float,
    ): List<RectF> {
      val half = size / 2f
      return when (count) {
        1 -> {
          listOf(RectF(0f, 0f, size, size))
        }

        2 -> {
          listOf(
            RectF(0f, 0f, half, size),
            RectF(half, 0f, size, size),
          )
        }

        3 -> {
          listOf(
            RectF(0f, 0f, half, size),
            RectF(half, 0f, size, half),
            RectF(half, half, size, size),
          )
        }

        else -> {
          listOf(
            RectF(0f, 0f, half, half),
            RectF(half, 0f, size, half),
            RectF(0f, half, half, size),
            RectF(half, half, size, size),
          )
        }
      }
    }

    private fun Canvas.drawCenterCrop(
      bitmap: Bitmap,
      rect: RectF,
      paint: Paint,
    ) {
      val scale = max(rect.width() / bitmap.width, rect.height() / bitmap.height)
      val dx = rect.left + (rect.width() - bitmap.width * scale) / 2f
      val dy = rect.top + (rect.height() - bitmap.height * scale) / 2f

      val matrix =
        Matrix().apply {
          setScale(scale, scale)
          postTranslate(dx, dy)
        }

      save()
      clipRect(rect)
      drawBitmap(bitmap, matrix, paint)
      restore()
    }

    companion object {
      private const val MAX_TILES = 4
    }
  }
