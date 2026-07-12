package org.grakovne.lissen.content

import android.net.Uri
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedBookmarkProvider
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.content.folder.FolderRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.domain.isSame
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenMediaProvider
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
    private val channelProvider: AudiobookshelfChannelProvider,
    private val localCacheRepository: LocalCacheRepository,
    private val cachedCoverProvider: CachedCoverProvider,
    private val cachedBookmarkProvider: CachedBookmarkProvider,
    private val folderRepository: FolderRepository,
  ) {
    suspend fun dropBookmark(bookmark: Bookmark) {
      Timber.d("Dropping bookmark for ${bookmark.libraryItemId} at position=${bookmark.totalPosition.toInt()}s")
      cachedBookmarkProvider.dropBookmark(bookmark = bookmark)
    }

    suspend fun createBookmark(
      title: String,
      libraryItemId: String,
      totalPosition: Double,
    ): Bookmark {
      Timber.d("Creating bookmark for $libraryItemId at position=${totalPosition.toInt()}s")
      return cachedBookmarkProvider
        .createBookmark(
          title = title,
          libraryItemId = libraryItemId,
          totalTime = totalPosition,
        )
    }

    suspend fun provideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .provideBookmarks(playingItemId)
        .sortedByDescending { it.createdAt }
        .fold(emptyList()) { acc, item -> if (acc.any { it.isSame(item) }) acc else acc + item }

    suspend fun updateAndProvideBookmarks(playingItemId: String): List<Bookmark> =
      cachedBookmarkProvider
        .fetchBookmarks(playingItemId)
        .sortedByDescending { it.createdAt }
        .fold(emptyList()) { acc, b -> if (acc.any { it.isSame(b) }) acc else acc + b }

    fun provideFileUri(
      libraryItemId: String,
      chapterId: String,
    ): OperationResult<Uri> {
      Timber.d("Resolving file URI: bookId=$libraryItemId, chapterId=$chapterId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository
            .provideFileUri(libraryItemId, chapterId)
            ?.let { OperationResult.Success(it) }
            ?: OperationResult.Error(OperationError.InternalError)
        }

        false -> {
          localCacheRepository
            .provideFileUri(libraryItemId, chapterId)
            ?.let { OperationResult.Success(it) }
            ?: providePreferredChannel()
              .provideFileUri(libraryItemId, chapterId)
              .let { OperationResult.Success(it) }
        }
      }
    }

    suspend fun syncProgress(
      sessionId: String,
      detailedItem: DetailedItem,
      progress: PlaybackProgress,
    ): OperationResult<Unit> {
      Timber.d(
        "Syncing progress: bookId=${detailedItem.id}, totalTime=${progress.currentTotalTime.toInt()}s, chapterTime=${progress.currentChapterTime.toInt()}s",
      )

      localCacheRepository.syncProgress(detailedItem, progress)

      val channelSyncResult =
        providePreferredChannel()
          .syncProgress(sessionId, progress)

      return when (preferences.isForceCache()) {
        true -> OperationResult.Success(Unit)
        false -> channelSyncResult
      }
    }

    suspend fun markAsListened(
      itemId: String,
      isFinished: Boolean,
    ): OperationResult<Unit> {
      Timber.d("Marking as listened: bookId=$itemId, isFinished=$isFinished")

      localCacheRepository.updateFinishedState(itemId, isFinished)

      return when (preferences.isForceCache()) {
        true -> OperationResult.Success(Unit)
        false -> providePreferredChannel().updateListenedState(itemId, isFinished)
      }
    }

    suspend fun fetchBookCover(bookId: String): OperationResult<File> {
      Timber.d("Fetching book cover: bookId=$bookId")
      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchBookCover(bookId)
        }

        false -> {
          cachedCoverProvider.provideCover(
            channel = providePreferredChannel(),
            itemId = bookId,
          )
        }
      }
    }

    suspend fun fetchAuthorCover(authorId: String): OperationResult<File> {
      Timber.d("Fetching author cover: authorId=$authorId")
      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchAuthorCover(authorId)
        }

        false -> {
          cachedCoverProvider.provideAuthorCover(
            channel = providePreferredChannel(),
            authorId = authorId,
          )
        }
      }
    }

    suspend fun searchBooks(
      libraryId: String,
      query: String,
      limit: Int,
    ): OperationResult<List<Book>> {
      Timber.d("Searching books: libraryId=$libraryId, query='$query'")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.searchBooks(libraryId = libraryId, query = query)
        }

        false -> {
          val foldedIds = folderRepository.foldedBookIds()
          providePreferredChannel()
            .searchBooks(
              libraryId = libraryId,
              query = query,
              limit = limit,
            ).map { books -> books.filterNot { it.id in foldedIds } }
        }
      }
    }

    suspend fun fetchBooks(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<Book>> {
      Timber.d("Fetching books: libraryId=$libraryId, page=$pageNumber, pageSize=$pageSize")

      return when (preferences.isForceCache()) {
        true -> localCacheRepository.fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
        false -> providePreferredChannel().fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = pageNumber)
      }
    }

    suspend fun fetchLibrary(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
    ): OperationResult<PagedItems<LibraryEntry>> {
      Timber.d("Fetching library: libraryId=$libraryId, page=$pageNumber, pageSize=$pageSize")

      val grouping = preferences.getLibraryGrouping()

      if (preferences.isForceCache()) {
        return localCacheRepository.fetchLibrary(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          libraryGrouping = grouping,
        )
      }

      val foldedIds = folderRepository.foldedBookIds()
      val downloadedFirst = preferences.getDownloadedFirst()

      if (grouping != LibraryGrouping.NONE && (downloadedFirst || foldedIds.isNotEmpty())) {
        return fetchGroupedLibrary(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          grouping = grouping,
          foldedIds = foldedIds,
          downloadedFirst = downloadedFirst,
        )
      }

      if (downloadedFirst) {
        return fetchLibraryDownloadedFirst(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          foldedIds = foldedIds,
        )
      }

      return fetchRemoteLibrary(
        libraryId = libraryId,
        pageSize = pageSize,
        pageNumber = pageNumber,
        grouping = grouping,
        foldedIds = foldedIds,
      )
    }

    private suspend fun fetchRemoteLibrary(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      grouping: LibraryGrouping,
      foldedIds: Set<String>,
    ): OperationResult<PagedItems<LibraryEntry>> {
      if (foldedIds.isEmpty()) {
        return providePreferredChannel().fetchLibrary(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          libraryGrouping = grouping,
        )
      }

      val startIndex = pageNumber * pageSize
      val remoteFetchSize = startIndex + pageSize + foldedIds.size

      return providePreferredChannel()
        .fetchLibrary(
          libraryId = libraryId,
          pageSize = remoteFetchSize,
          pageNumber = 0,
          libraryGrouping = grouping,
        ).map { paged ->
          PagedItems(
            items =
              paged.items
                .filterNot { it is LibraryEntry.BookEntry && it.book.id in foldedIds }
                .drop(startIndex)
                .take(pageSize),
            currentPage = pageNumber,
            totalItems = paged.totalItems,
          )
        }
    }

    private suspend fun fetchLibraryDownloadedFirst(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      foldedIds: Set<String>,
    ): OperationResult<PagedItems<LibraryEntry>> {
      val cachedBooks = localCacheRepository.fetchCachedBooks(libraryId).filterNot { it.id in foldedIds }
      if (cachedBooks.isEmpty()) {
        return fetchRemoteLibrary(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = pageNumber,
          grouping = LibraryGrouping.NONE,
          foldedIds = foldedIds,
        )
      }

      val cachedIds = cachedBooks.mapTo(mutableSetOf()) { it.id }
      val startIndex = pageNumber * pageSize
      val downloadedEntries =
        cachedBooks
          .drop(startIndex)
          .take(pageSize)
          .map { LibraryEntry.BookEntry(it) }

      val remaining = pageSize - downloadedEntries.size
      val remoteOffset = (startIndex - cachedBooks.size).coerceAtLeast(0)
      val remoteFetchSize =
        when (remaining) {
          0 -> 1 + foldedIds.size
          else -> remoteOffset + remaining + cachedIds.size + foldedIds.size
        }

      return providePreferredChannel()
        .fetchLibrary(
          libraryId = libraryId,
          pageSize = remoteFetchSize,
          pageNumber = 0,
          libraryGrouping = LibraryGrouping.NONE,
        ).map { paged ->
          val remoteEntries =
            paged.items
              .filterNot { it is LibraryEntry.BookEntry && (it.book.id in cachedIds || it.book.id in foldedIds) }
              .drop(remoteOffset)
              .take(remaining)

          PagedItems(
            items = downloadedEntries + remoteEntries,
            currentPage = pageNumber,
            totalItems = paged.totalItems,
          )
        }
    }

    private suspend fun fetchGroupedLibrary(
      libraryId: String,
      pageSize: Int,
      pageNumber: Int,
      grouping: LibraryGrouping,
      foldedIds: Set<String>,
      downloadedFirst: Boolean,
    ): OperationResult<PagedItems<LibraryEntry>> {
      val cachedBooks =
        when (downloadedFirst) {
          true -> cachedDetailedItems(libraryId).filterNot { it.id in foldedIds }
          false -> emptyList()
        }

      return providePreferredChannel()
        .fetchLibrary(
          libraryId = libraryId,
          pageSize = pageSize,
          pageNumber = 0,
          libraryGrouping = grouping,
        ).flatMap { firstPage ->
          val allEntriesResult =
            when {
              firstPage.totalItems <= pageSize -> {
                OperationResult.Success(firstPage)
              }

              else -> {
                providePreferredChannel().fetchLibrary(
                  libraryId = libraryId,
                  pageSize = firstPage.totalItems,
                  pageNumber = 0,
                  libraryGrouping = grouping,
                )
              }
            }

          allEntriesResult.map { allEntries ->
            val filtered =
              allEntries.items.filterNot { it is LibraryEntry.BookEntry && it.book.id in foldedIds }
            val ordered =
              when (downloadedFirst) {
                true -> {
                  val (downloaded, other) = filtered.partition { it.containsDownloadedBook(cachedBooks) }
                  downloaded + other
                }

                false -> {
                  filtered
                }
              }
            val startIndex = pageNumber * pageSize

            PagedItems(
              items = ordered.drop(startIndex).take(pageSize),
              currentPage = pageNumber,
              totalItems = ordered.size,
            )
          }
        }
    }

    private suspend fun cachedDetailedItems(libraryId: String): List<DetailedItem> =
      localCacheRepository
        .fetchDetailedItems()
        .fold(
          onSuccess = { page -> page.items.filter { it.libraryId == libraryId } },
          onFailure = { emptyList() },
        )

    private fun LibraryEntry.containsDownloadedBook(cachedBooks: List<DetailedItem>): Boolean =
      when (this) {
        is LibraryEntry.BookEntry -> {
          cachedBooks.any { it.id == book.id }
        }

        is LibraryEntry.SeriesEntry -> {
          cachedBooks.any { cached ->
            cached.series.any { series -> series.id == id || series.name.matchesGroup(title) }
          }
        }

        is LibraryEntry.AuthorEntry -> {
          cachedBooks.any { cached ->
            cached.authors.any { author -> author.id == id || author.name.matchesGroup(name) } ||
              cached.author.matchesGroup(name)
          }
        }

        is LibraryEntry.FolderEntry -> {
          false
        }
      }

    private fun String?.matchesGroup(groupName: String): Boolean =
      this
        ?.trim()
        ?.equals(groupName.trim(), ignoreCase = true) == true

    suspend fun fetchSeriesItems(
      libraryId: String,
      seriesId: String,
    ): OperationResult<List<Book>> {
      Timber.d("Fetching series items: libraryId=$libraryId, seriesId=$seriesId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchSeriesItems(libraryId = libraryId, seriesId = seriesId)
        }

        false -> {
          val foldedIds = folderRepository.foldedBookIds()
          val cachedIds =
            when (preferences.getDownloadedFirst()) {
              true -> cachedDetailedItems(libraryId).mapTo(mutableSetOf()) { it.id }
              false -> emptySet()
            }
          providePreferredChannel()
            .fetchSeriesItems(libraryId = libraryId, seriesId = seriesId)
            .map { books ->
              books
                .filterNot { it.id in foldedIds }
                .prioritizeDownloaded(cachedIds)
            }
        }
      }
    }

    suspend fun fetchAuthorBooks(
      libraryId: String,
      authorId: String,
    ): OperationResult<List<Book>> {
      Timber.d("Fetching author books: libraryId=$libraryId, authorId=$authorId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchAuthorItems(libraryId = libraryId, authorId = authorId)
        }

        false -> {
          val foldedIds = folderRepository.foldedBookIds()
          val cachedIds =
            when (preferences.getDownloadedFirst()) {
              true -> cachedDetailedItems(libraryId).mapTo(mutableSetOf()) { it.id }
              false -> emptySet()
            }
          providePreferredChannel()
            .fetchAuthorBooks(libraryId = libraryId, authorId = authorId)
            .map { books ->
              books
                .filterNot { it.id in foldedIds }
                .prioritizeDownloaded(cachedIds)
            }
        }
      }
    }

    private fun List<Book>.prioritizeDownloaded(cachedIds: Set<String>): List<Book> {
      if (cachedIds.isEmpty()) return this
      val (downloaded, other) = partition { it.id in cachedIds }
      return downloaded + other
    }

    suspend fun fetchLibraries(): OperationResult<List<Library>> {
      Timber.d("Fetching libraries: source=${if (preferences.isForceCache()) "cache" else "network"}")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchLibraries()
        }

        false -> {
          providePreferredChannel()
            .fetchLibraries()
            .also {
              it.foldAsync(
                onSuccess = { libraries -> localCacheRepository.updateLibraries(libraries) },
                onFailure = {},
              )
            }
        }
      }
    }

    suspend fun startPlayback(
      itemId: String,
      chapterId: String,
      supportedMimeTypes: List<String>,
      deviceId: String,
    ): OperationResult<PlaybackSession> {
      Timber.d("Starting playback: itemId=$itemId, chapterId=$chapterId, mimeTypes=$supportedMimeTypes")

      return providePreferredChannel()
        .startPlayback(
          bookId = itemId,
          episodeId = chapterId,
          supportedMimeTypes = supportedMimeTypes,
          deviceId = deviceId,
        ).foldAsync(
          onSuccess = {
            OperationResult.Success(it)
          },
          onFailure = {
            OperationResult.Success(PlaybackSession.local(itemId))
          },
        )
    }

    suspend fun fetchRecentListenedBooks(libraryId: String): OperationResult<List<RecentBook>> {
      Timber.d("Fetching recent books: libraryId=$libraryId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository.fetchRecentListenedBooks(libraryId)
        }

        false -> {
          val foldedIds = folderRepository.foldedBookIds()
          providePreferredChannel()
            .fetchRecentListenedBooks(libraryId)
            .map { items -> syncFromLocalProgress(libraryId = libraryId, detailedItems = items) }
            .map { books -> books.filterNot { it.id in foldedIds } }
        }
      }
    }

    suspend fun fetchBook(bookId: String): OperationResult<DetailedItem> {
      Timber.d("Fetching book: bookId=$bookId")

      return when (preferences.isForceCache()) {
        true -> {
          localCacheRepository
            .fetchBook(bookId)
            ?.let { OperationResult.Success(it) }
            ?: OperationResult.Error(OperationError.InternalError)
        }

        false -> {
          providePreferredChannel()
            .fetchBook(bookId)
            .map { syncFromLocalProgress(it) }
            .map { trimProgress(it) }
        }
      }
    }

    suspend fun authorize(
      host: String,
      username: String,
      password: String,
    ): OperationResult<UserAccount> {
      Timber.d("Authorizing for $host")
      return provideAuthService().authorize(host, username, password) { onPostLogin(host, it) }
    }

    suspend fun startOAuth(
      host: String,
      onSuccess: () -> Unit,
      onFailure: (OperationError) -> Unit,
    ) {
      Timber.d("Starting OAuth for $host")

      return provideAuthService()
        .startOAuth(
          host = host,
          onSuccess = onSuccess,
          onFailure = { onFailure(it) },
        )
    }

    suspend fun onPostLogin(
      host: String,
      account: UserAccount,
    ) {
      Timber.d("Post-login setup for $host")
      provideAuthService()
        .persistCredentials(
          host = host,
          username = account.username,
          token = account.token,
          accessToken = account.accessToken,
          refreshToken = account.refreshToken,
        )

      wipeFoldersOnServerChange(host)

      fetchLibraries()
        .fold(
          onSuccess = {
            val preferredLibrary =
              it
                .find { item -> item.id == account.preferredLibraryId }
                ?: it.firstOrNull()

            preferredLibrary
              ?.let { library ->
                preferences.savePreferredLibrary(
                  Library(
                    id = library.id,
                    title = library.title,
                    type = library.type,
                  ),
                )
              }
          },
          onFailure = {
            account
              .preferredLibraryId
              ?.let { library ->
                Library(
                  id = library,
                  title = "Default Library",
                  type = LibraryType.LIBRARY,
                )
              }?.let { preferences.savePreferredLibrary(it) }
          },
        )
    }

    /**
     * Folders store server-scoped book ids. If we just logged into a different host than the one the
     * existing folders were created against, those ids are dead — wipe them. Re-login to the same host
     * leaves folders untouched. Requires [persistCredentials] to have already saved the new host.
     */
    private suspend fun wipeFoldersOnServerChange(host: String) {
      if (host != preferences.getFoldersHost() && folderRepository.folderCount() > 0) {
        Timber.i("Server changed (folders belonged to ${preferences.getFoldersHost()}, now $host); wiping local folders")
        folderRepository.clear()
        preferences.saveFoldersHost(host)
      }
    }

    private suspend fun syncFromLocalProgress(
      libraryId: String,
      detailedItems: List<RecentBook>,
    ): List<RecentBook> {
      val localRecentlyBooks =
        localCacheRepository
          .fetchRecentListenedBooks(libraryId)
          .fold(
            onSuccess = { it },
            onFailure = { return@fold detailedItems },
          )

      val syncedRecentlyBooks =
        detailedItems
          .mapNotNull { item -> localRecentlyBooks.find { it.id == item.id }?.let { item to it } }
          .map { (remote, local) ->
            val localTimestamp = local.listenedLastUpdate ?: return@map remote
            val remoteTimestamp = remote.listenedLastUpdate ?: return@map remote

            when (remoteTimestamp > localTimestamp) {
              true -> remote
              false -> local
            }
          }

      return detailedItems
        .map { item ->
          syncedRecentlyBooks
            .find { item.id == it.id }
            ?.let { local -> item.copy(listenedPercentage = local.listenedPercentage) }
            ?: item
        }
    }

    private fun trimProgress(detailedItem: DetailedItem): DetailedItem {
      val totalDuration = detailedItem.chapters.sumOf { it.duration }
      val progress = detailedItem.progress?.currentTime ?: return detailedItem

      return when {
        progress <= 0 -> detailedItem.copy(progress = null)
        progress >= totalDuration -> detailedItem.copy(progress = null)
        else -> detailedItem
      }
    }

    private suspend fun syncFromLocalProgress(detailedItem: DetailedItem): DetailedItem {
      val cachedProgress = localCacheRepository.fetchPlayingItemProgress(detailedItem.id)
      val channelProgress = detailedItem.progress

      val updatedProgress =
        listOfNotNull(cachedProgress, channelProgress)
          .maxByOrNull { it.lastUpdate }
          ?: return detailedItem

      Timber.d(
        """
        Merging local playback progress into channel-fetched:
            Channel Progress: $channelProgress
            Cached Progress: $cachedProgress
            Final Progress: $updatedProgress
        """.trimIndent(),
      )

      return detailedItem.copy(progress = updatedProgress)
    }

    fun fetchConnectionHost() = providePreferredChannel().fetchConnectionHost()

    suspend fun fetchConnectionInfo() = providePreferredChannel().fetchConnectionInfo()

    fun provideAuthService(): ChannelAuthService = channelProvider.provideChannelAuth()

    fun providePreferredChannel(): MediaChannel = channelProvider.provideMediaChannel()
  }
