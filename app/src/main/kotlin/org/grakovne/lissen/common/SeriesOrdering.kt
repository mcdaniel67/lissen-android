package org.grakovne.lissen.common

import org.grakovne.lissen.domain.Book

fun Book.seriesSequence(): String? =
  series
    ?.substringAfterLast('#', "")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

fun List<Book>.sortedBySeriesPosition(): List<Book> = sortedWith(compareBy(nullsLast()) { it.seriesSequence()?.toDoubleOrNull() })

fun Book.seriesName(): String? =
  series
    ?.substringBeforeLast('#')
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/**
 * Orders an author's books so that same-series titles cluster together in reading order,
 * with standalone (series-less) titles falling to the end alphabetically.
 */
fun List<Book>.sortedBySeriesThenPosition(): List<Book> =
  sortedWith(
    compareBy<Book, String?>(nullsLast()) { it.seriesName() }
      .thenBy(nullsLast<Double>()) { it.seriesSequence()?.toDoubleOrNull() }
      .thenBy { it.title },
  )
