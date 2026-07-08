package org.grakovne.lissen.domain

sealed interface BookDownloadState {
  data object NotDownloaded : BookDownloadState

  data class Downloading(
    val progress: Double,
  ) : BookDownloadState

  data object Downloaded : BookDownloadState
}
