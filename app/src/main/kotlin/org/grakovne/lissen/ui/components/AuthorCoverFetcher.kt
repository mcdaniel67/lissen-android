package org.grakovne.lissen.ui.components

import coil3.ImageLoader
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import java.io.File

data class AuthorCoverKey(
  val authorId: String,
  val authorName: String,
)

class AuthorCoverFetcher(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: LissenMediaProvider,
  private val authorId: String,
  private val authorName: String,
  options: Options,
) : ImageFetcher(options) {
  override suspend fun resolve(): OperationResult<File> =
    when {
      localOnly -> localCacheRepository.fetchAuthorCover(authorName)
      else -> mediaChannel.fetchAuthorCover(authorId)
    }
}

class AuthorCoverFetcherFactory(
  private val localCacheRepository: LocalCacheRepository,
  private val mediaChannel: LissenMediaProvider,
) : Fetcher.Factory<AuthorCoverKey> {
  override fun create(
    data: AuthorCoverKey,
    options: Options,
    imageLoader: ImageLoader,
  ): AuthorCoverFetcher =
    AuthorCoverFetcher(
      localCacheRepository = localCacheRepository,
      mediaChannel = mediaChannel,
      authorId = data.authorId,
      authorName = data.authorName,
      options = options,
    )
}

class AuthorCoverKeyer : Keyer<AuthorCoverKey> {
  override fun key(
    data: AuthorCoverKey,
    options: Options,
  ): String = "author:${data.authorId}"
}
