package org.grakovne.lissen.content.cache.temporary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.common.SeriesCoverComposer
import org.grakovne.lissen.content.cache.common.writeToFile
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesCoverProvider
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
    private val composer: SeriesCoverComposer,
    private val properties: ShortTermCacheStorageProperties,
  ) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun provideSeriesCover(
      seriesId: String,
      coverItemIds: List<String>,
    ): OperationResult<File> {
      val key = cacheKey(seriesId, coverItemIds)
      val lock = locks.computeIfAbsent(key) { Mutex() }

      return lock.withLock {
        when (val cover = fetchCached(key)) {
          null -> cache(key, coverItemIds).also { Timber.d("Composing series cover $seriesId") }
          else -> OperationResult.Success(cover).also { Timber.d("Fetched cached series cover $seriesId") }
        }
      }
    }

    fun clearCache() =
      properties
        .provideSeriesCoverCacheFolder()
        .deleteRecursively()
        .also { Timber.d("Clear series cover short-term cache") }

    private suspend fun fetchCached(key: String): File? =
      withContext(Dispatchers.IO) {
        properties
          .provideSeriesCoverPath(key)
          .let { cached ->
            when {
              cached.isFile && cached.length() > 0L -> {
                cached
              }

              else -> {
                cached.delete()
                null
              }
            }
          }
      }

    private suspend fun cache(
      key: String,
      coverItemIds: List<String>,
    ): OperationResult<File> {
      val covers =
        coroutineScope {
          coverItemIds
            .map { itemId ->
              async {
                mediaProvider
                  .fetchBookCover(itemId)
                  .fold(onSuccess = { it }, onFailure = { null })
              }
            }.awaitAll()
            .filterNotNull()
        }

      val composed = composer.compose(covers) ?: return OperationResult.Error(OperationError.InternalError)

      return withContext(Dispatchers.IO) {
        val dest = properties.provideSeriesCoverPath(key)
        dest.parentFile?.mkdirs()

        if (covers.size != coverItemIds.size) {
          Timber.w("Caching partial series cover for $key (${covers.size}/${coverItemIds.size})")
        }

        persist(composed, dest)
      }
    }

    private fun persist(
      composed: Buffer,
      dest: File,
    ): OperationResult<File> {
      val temp = File.createTempFile("${dest.name}.", ".tmp", dest.parentFile)

      return try {
        composed.writeToFile(temp)
        check(temp.renameTo(dest)) { "Unable to move composed cover into cache" }
        OperationResult.Success(dest)
      } catch (ex: Exception) {
        Timber.e(ex, "Unable to cache composed cover ${dest.name}")
        OperationResult.Error(OperationError.InternalError, ex.message)
      } finally {
        temp.delete()
      }
    }

    private fun cacheKey(
      seriesId: String,
      coverItemIds: List<String>,
    ): String =
      MessageDigest
        .getInstance("SHA-256")
        .digest(("$COMPOSITE_LAYOUT_VERSION:$seriesId:${coverItemIds.joinToString(",")}").toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
      // Bump to invalidate on-disk composites when the mosaic layout changes (e.g. fanned → 2×2 grid).
      private const val COMPOSITE_LAYOUT_VERSION = "v2-grid"
    }
  }
