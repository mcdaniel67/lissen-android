package org.grakovne.lissen.common

enum class LibraryGrouping {
  NONE,
  SERIES,
  AUTHOR,

  /**
   * Like [AUTHOR], but only authors with more than the configured threshold of books collapse
   * into a dropdown. Authors at or below the threshold have their books flattened inline as
   * ordinary rows, sorted by series.
   */
  AUTHOR_SMART,
}
