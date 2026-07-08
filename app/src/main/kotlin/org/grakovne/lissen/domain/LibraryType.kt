package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
enum class LibraryType {
  LIBRARY,
  PODCAST,
  UNKNOWN,
  ;

  companion object {
    // Audiobooks-only fork: podcast libraries are never offered for selection.
    // PODCAST/UNKNOWN remain valid enum values (the server still reports them).
    val meaningfulTypes = listOf(LIBRARY)
  }
}
