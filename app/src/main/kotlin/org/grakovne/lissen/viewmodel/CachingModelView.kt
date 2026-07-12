package org.grakovne.lissen.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
import org.grakovne.lissen.content.cache.persistent.ContentCachingProgress
import org.grakovne.lissen.content.cache.persistent.ContentCachingService
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.content.cache.temporary.SeriesCoverProvider
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.BookDownloadState
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.ContentCachingTask
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.settings.advanced.cache.CachedItemsPageSource
import timber.log.Timber
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class CachingModelView
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val localCacheRepository: LocalCacheRepository,
    private val contentCachingProgress: ContentCachingProgress,
    private val contentCachingManager: ContentCachingManager,
    private val preferences: LissenSharedPreferences,
    private val cachedCoverProvider: CachedCoverProvider,
    private val seriesCoverProvider: SeriesCoverProvider,
  ) : ViewModel() {
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    val forceCache = preferences.forceCacheFlow

    /**
     * Ids of all locally-cached books, observed once for the whole library list so rows can render a
     * download badge without each subscribing its own database Flow.
     */
    val cachedBookIds: StateFlow<Set<String>> =
      localCacheRepository
        .observeCachedBookIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _bookCachingProgress = mutableMapOf<String, MutableStateFlow<CacheState>>()

    private val _runningDownloads = MutableStateFlow<Map<String, Double>>(emptyMap())

    /**
     * In-session progress of books currently caching, keyed by book id, observed once for the
     * whole library list so rows can render a downloading ring without each subscribing its own
     * per-book progress flow.
     */
    val runningDownloads: StateFlow<Map<String, Double>> = _runningDownloads.asStateFlow()

    private val pageConfig =
      PagingConfig(
        pageSize = PAGE_SIZE,
        initialLoadSize = PAGE_SIZE,
        prefetchDistance = PAGE_SIZE,
      )

    private var pageSource: PagingSource<Int, DetailedItem>? = null
    val libraryPager: Flow<PagingData<DetailedItem>> by lazy {
      Pager(
        config = pageConfig,
        pagingSourceFactory = {
          val source = CachedItemsPageSource(localCacheRepository) { _totalCount.value = it }

          pageSource = source
          source
        },
      ).flow.cachedIn(viewModelScope)
    }

    init {
      viewModelScope.launch {
        contentCachingProgress.statusFlow.collect { (itemId, progress) ->
          val flow =
            _bookCachingProgress.getOrPut(itemId) {
              MutableStateFlow(progress)
            }
          flow.value = progress

          _runningDownloads.update { running ->
            when (progress.status) {
              is CacheStatus.Caching -> running + (itemId to progress.progress)
              else -> running - itemId
            }
          }
        }
      }
    }

    suspend fun clearShortTermCache() {
      withContext(Dispatchers.IO) {
        cachedCoverProvider.clearCache()
        seriesCoverProvider.clearCache()
      }
    }

    fun cache(
      mediaItem: DetailedItem,
      currentPosition: Double,
      option: DownloadOption,
    ) {
      Timber.d("User action: cache ${mediaItem.id}, option=$option, position=${currentPosition.toInt()}s")
      startCaching(mediaItem.id, currentPosition, option)
    }

    /**
     * Fire-and-forget bulk download for a multi-selection: enqueues a full-book caching task per id.
     * The caching service fetches each book and registers an independent job, so per-book progress
     * surfaces through the same [runningDownloads] / [downloadState] flows as a single download and
     * callers don't wait on this.
     */
    fun cacheByIds(bookIds: Collection<String>) {
      Timber.d("User action: cacheByIds ${bookIds.size} items")
      bookIds.forEach { startCaching(it, 0.0, AllItemsDownloadOption) }
    }

    private fun startCaching(
      itemId: String,
      currentPosition: Double,
      option: DownloadOption,
    ) {
      val task =
        ContentCachingTask(
          itemId = itemId,
          options = option,
          currentPosition = currentPosition,
        )

      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          action = ContentCachingService.CACHE_ITEM_ACTION
          putExtra(ContentCachingService.CACHING_TASK_EXTRA, task as Serializable)
        }

      context.startForegroundService(intent)
    }

    fun getProgress(bookId: String) =
      _bookCachingProgress
        .getOrPut(bookId) { MutableStateFlow(CacheState(CacheStatus.Idle)) }

    /**
     * Single source of truth for "what is the download state of this book" — combines the live
     * in-progress caching flow with membership in the completed-download set so every surface
     * (library rows, group rows, player nav bar) can render a consistent download badge.
     */
    fun downloadState(bookId: String): Flow<BookDownloadState> =
      combine(getProgress(bookId), cachedBookIds) { progress, cachedIds ->
        when {
          bookId in cachedIds -> BookDownloadState.Downloaded
          progress.status is CacheStatus.Caching -> BookDownloadState.Downloading(progress.progress)
          else -> BookDownloadState.NotDownloaded
        }
      }

    /**
     * Pure resolver mirroring [downloadState] for list-wide rendering: rows compute their state
     * from a single observed [cachedBookIds] set and [runningDownloads] map instead of each
     * opening a per-book flow.
     */
    fun downloadStateOf(
      bookId: String,
      cachedIds: Set<String>,
      running: Map<String, Double>,
    ): BookDownloadState =
      when {
        bookId in cachedIds -> BookDownloadState.Downloaded
        running.containsKey(bookId) -> BookDownloadState.Downloading(running.getValue(bookId))
        else -> BookDownloadState.NotDownloaded
      }

    suspend fun dropCache(bookId: String) {
      Timber.d("User action: dropCache $bookId")
      contentCachingManager.dropCache(bookId)
    }

    fun stopCaching(item: DetailedItem) {
      Timber.d("User action: stopCaching ${item.id}")
      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          action = ContentCachingService.STOP_CACHING_ACTION
          putExtra(ContentCachingService.CACHING_ITEM_ID, item.id)
        }

      context.startForegroundService(intent)
    }

    suspend fun dropCache(
      item: DetailedItem,
      chapter: PlayingChapter,
    ) {
      Timber.d("User action: dropCache ${item.id}, chapter=${chapter.id}")
      contentCachingManager.dropCache(item, chapter)
    }

    fun setForceCache(enabled: Boolean) {
      val currentlyEnabled = localCacheUsing()
      Timber.d("User action: setForceCache (enabled=$enabled, current=$currentlyEnabled)")
      if (enabled == currentlyEnabled) return

      when (enabled) {
        true -> preferences.enableForceCache()
        false -> preferences.disableForceCache()
      }
    }

    fun localCacheUsing() = preferences.isForceCache()

    fun getDownloadChaptersCount() = preferences.getDownloadChaptersCount()

    fun saveDownloadChaptersCount(count: Int) = preferences.saveDownloadChaptersCount(count)

    fun provideCacheState(bookId: String): Flow<Boolean> = contentCachingManager.hasMetadataCached(bookId)

    fun provideCacheState(
      bookId: String,
      chapterId: String,
    ): Flow<Boolean> = contentCachingManager.hasMetadataCached(bookId, chapterId)

    fun fetchCachedItems() {
      viewModelScope.launch {
        withContext(Dispatchers.IO) {
          pageSource?.invalidate()
        }
      }
    }

    suspend fun fetchLatestUpdate(libraryId: String) = localCacheRepository.fetchLatestUpdate(libraryId)

    companion object {
      private const val PAGE_SIZE = 20
    }
  }
