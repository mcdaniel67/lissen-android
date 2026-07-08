package org.grakovne.lissen.content

import android.net.Uri
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
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
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LissenMediaProviderTest {
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val channelProvider = mockk<AudiobookshelfChannelProvider>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)
  private val cachedCoverProvider = mockk<CachedCoverProvider>(relaxed = true)
  private val cachedBookmarkProvider = mockk<CachedBookmarkProvider>(relaxed = true)
  private val folderRepository = mockk<FolderRepository>(relaxed = true)
  private val mediaChannel = mockk<MediaChannel>(relaxed = true)
  private val authService = mockk<ChannelAuthService>(relaxed = true)

  private lateinit var provider: LissenMediaProvider

  @BeforeEach
  fun setup() {
    every { channelProvider.provideMediaChannel() } returns mediaChannel
    every { channelProvider.provideChannelAuth() } returns authService
    provider =
      LissenMediaProvider(
        preferences,
        channelProvider,
        localCacheRepository,
        cachedCoverProvider,
        cachedBookmarkProvider,
        folderRepository,
      )
  }

  @Nested
  inner class FetchBook {
    @Test
    fun `returns from local cache when force cache enabled and cache hit`() =
      runBlocking {
        val item = detailedItem("book-1")
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook("book-1") } returns item

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("book-1", (result as OperationResult.Success).data.id)
      }

    @Test
    fun `returns Error when force cache enabled and cache miss`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook("book-1") } returns null

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
      }

    @Test
    fun `does not call channel when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchBook(any()) } returns null

        provider.fetchBook("book-1")

        coVerify(exactly = 0) { mediaChannel.fetchBook(any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchBook("book-1") } returns OperationResult.Success(item)
        coEvery { localCacheRepository.fetchPlayingItemProgress("book-1") } returns null

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { mediaChannel.fetchBook("book-1") }
      }

    @Test
    fun `returns Error when channel call fails and force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchBook(any()) } returns
          OperationResult.Error(OperationError.NetworkError)

        val result = provider.fetchBook("book-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class OnPostLoginFolderWipe {
    @BeforeEach
    fun stubLogin() {
      every { preferences.isForceCache() } returns false
      coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(emptyList())
    }

    @Test
    fun `wipes folders when logging into a different host with existing folders`() =
      runBlocking {
        every { preferences.getFoldersHost() } returns "https://server-a.example"
        coEvery { folderRepository.folderCount() } returns 2

        provider.onPostLogin("https://server-b.example", account())

        coVerify(exactly = 1) { folderRepository.clear() }
        verify(exactly = 1) { preferences.saveFoldersHost("https://server-b.example") }
      }

    @Test
    fun `does not wipe folders when re-logging into the same host`() =
      runBlocking {
        every { preferences.getFoldersHost() } returns "https://server-a.example"
        coEvery { folderRepository.folderCount() } returns 2

        provider.onPostLogin("https://server-a.example", account())

        coVerify(exactly = 0) { folderRepository.clear() }
        verify(exactly = 0) { preferences.saveFoldersHost(any()) }
      }

    @Test
    fun `does not wipe when host differs but no folders exist`() =
      runBlocking {
        every { preferences.getFoldersHost() } returns "https://server-a.example"
        coEvery { folderRepository.folderCount() } returns 0

        provider.onPostLogin("https://server-b.example", account())

        coVerify(exactly = 0) { folderRepository.clear() }
        verify(exactly = 0) { preferences.saveFoldersHost(any()) }
      }

    private fun account() =
      UserAccount(
        token = "token",
        accessToken = null,
        refreshToken = null,
        username = "user",
        preferredLibraryId = null,
      )
  }

  @Nested
  inner class FetchLibraries {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        val libs = listOf(Library("l1", "Books", LibraryType.LIBRARY))
        every { preferences.isForceCache() } returns true
        coEvery { localCacheRepository.fetchLibraries() } returns OperationResult.Success(libs)

        val result = provider.fetchLibraries()

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { localCacheRepository.fetchLibraries() }
        coVerify(exactly = 0) { mediaChannel.fetchLibraries() }
      }

    @Test
    fun `uses channel and updates local cache when force cache disabled`() =
      runBlocking {
        val libs = listOf(Library("l1", "Books", LibraryType.LIBRARY))
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(libs)

        val result = provider.fetchLibraries()

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { mediaChannel.fetchLibraries() }
        coVerify { localCacheRepository.updateLibraries(libs) }
      }

    @Test
    fun `does not update local cache on channel failure`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchLibraries() } returns
          OperationResult.Error(OperationError.NetworkError)

        provider.fetchLibraries()

        coVerify(exactly = 0) { localCacheRepository.updateLibraries(any()) }
      }
  }

  @Nested
  inner class FetchBooks {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        val paged = PagedItems(items = listOf<Book>(), currentPage = 0, totalItems = 0)
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.fetchBooks(libraryId = "l1", pageSize = 10, pageNumber = 0)
        } returns OperationResult.Success(paged)

        provider.fetchBooks("l1", 10, 0)

        coVerify { localCacheRepository.fetchBooks("l1", 10, 0) }
        coVerify(exactly = 0) { mediaChannel.fetchBooks(any(), any(), any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        val paged = PagedItems(items = listOf<Book>(), currentPage = 0, totalItems = 0)
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.fetchBooks(libraryId = "l1", pageSize = 10, pageNumber = 0)
        } returns OperationResult.Success(paged)

        provider.fetchBooks("l1", 10, 0)

        coVerify { mediaChannel.fetchBooks("l1", 10, 0) }
      }
  }

  @Nested
  inner class FetchLibrary {
    @Test
    fun `downloaded first returns cached books before remote books on first page`() =
      runBlocking {
        val cached = listOf(book("cached-1"), book("cached-2"))
        val remote =
          listOf(
            LibraryEntry.BookEntry(book("remote-1")),
            LibraryEntry.BookEntry(book("cached-1")),
            LibraryEntry.BookEntry(book("remote-2")),
          )

        every { preferences.isForceCache() } returns false
        every { preferences.getLibraryGrouping() } returns LibraryGrouping.NONE
        every { preferences.getDownloadedFirst() } returns true
        coEvery { localCacheRepository.fetchCachedBooks("l1") } returns cached
        coEvery {
          mediaChannel.fetchLibrary("l1", 3, 0, LibraryGrouping.NONE)
        } returns OperationResult.Success(PagedItems(remote, currentPage = 0, totalItems = 5))

        val result = provider.fetchLibrary("l1", pageSize = 3, pageNumber = 0)

        assertEquals(
          listOf("cached-1", "cached-2", "remote-1"),
          (result as OperationResult.Success).data.items.bookIds(),
        )
      }

    @Test
    fun `downloaded first uses global ordering across later pages`() =
      runBlocking {
        val cached = listOf(book("cached-1"), book("cached-2"))
        val remote =
          listOf(
            LibraryEntry.BookEntry(book("remote-1")),
            LibraryEntry.BookEntry(book("cached-1")),
            LibraryEntry.BookEntry(book("remote-2")),
            LibraryEntry.BookEntry(book("cached-2")),
            LibraryEntry.BookEntry(book("remote-3")),
            LibraryEntry.BookEntry(book("remote-4")),
          )

        every { preferences.isForceCache() } returns false
        every { preferences.getLibraryGrouping() } returns LibraryGrouping.NONE
        every { preferences.getDownloadedFirst() } returns true
        coEvery { localCacheRepository.fetchCachedBooks("l1") } returns cached
        coEvery {
          mediaChannel.fetchLibrary("l1", 6, 0, LibraryGrouping.NONE)
        } returns OperationResult.Success(PagedItems(remote, currentPage = 0, totalItems = 6))

        val result = provider.fetchLibrary("l1", pageSize = 3, pageNumber = 1)

        assertEquals(
          listOf("remote-2", "remote-3", "remote-4"),
          (result as OperationResult.Success).data.items.bookIds(),
        )
      }
  }

  @Nested
  inner class SearchBooks {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.searchBooks(libraryId = "l1", query = "test")
        } returns OperationResult.Success(emptyList())

        provider.searchBooks("l1", "test", 10)

        coVerify { localCacheRepository.searchBooks("l1", "test") }
        coVerify(exactly = 0) { mediaChannel.searchBooks(any(), any(), any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.searchBooks(libraryId = "l1", query = "test", limit = 10)
        } returns OperationResult.Success(emptyList())

        provider.searchBooks("l1", "test", 10)

        coVerify { mediaChannel.searchBooks("l1", "test", 10) }
      }
  }

  @Nested
  inner class FetchRecentListenedBooks {
    @Test
    fun `uses local cache when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true
        coEvery {
          localCacheRepository.fetchRecentListenedBooks("l1")
        } returns OperationResult.Success(emptyList())

        provider.fetchRecentListenedBooks("l1")

        coVerify { localCacheRepository.fetchRecentListenedBooks("l1") }
        coVerify(exactly = 0) { mediaChannel.fetchRecentListenedBooks(any()) }
      }

    @Test
    fun `uses channel when force cache disabled`() =
      runBlocking {
        val books = listOf(recentBook("book-1"))
        every { preferences.isForceCache() } returns false
        coEvery { mediaChannel.fetchRecentListenedBooks("l1") } returns
          OperationResult.Success(books)
        coEvery { localCacheRepository.fetchRecentListenedBooks("l1") } returns
          OperationResult.Success(emptyList())

        val result = provider.fetchRecentListenedBooks("l1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { mediaChannel.fetchRecentListenedBooks("l1") }
      }
  }

  @Nested
  inner class StartPlayback {
    @Test
    fun `returns remote session on channel success`() =
      runBlocking {
        val session = PlaybackSession.remote("session-1", "book-1")
        coEvery {
          mediaChannel.startPlayback(
            bookId = "book-1",
            episodeId = "ep-1",
            supportedMimeTypes = any(),
            deviceId = any(),
          )
        } returns OperationResult.Success(session)

        val result = provider.startPlayback("book-1", "ep-1", listOf("audio/mp3"), "device-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        assertEquals("session-1", (result as OperationResult.Success).data.sessionId)
      }

    @Test
    fun `returns local session on channel failure`() =
      runBlocking {
        coEvery {
          mediaChannel.startPlayback(any(), any(), any(), any())
        } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.startPlayback("book-1", "ep-1", listOf("audio/mp3"), "device-1")

        assertInstanceOf(OperationResult.Success::class.java, result)
        val session = (result as OperationResult.Success).data
        assertEquals("book-1", session.itemId)
        assertEquals(org.grakovne.lissen.domain.PlaybackSessionSource.LOCAL, session.sessionSource)
      }
  }

  @Nested
  inner class SyncProgress {
    @Test
    fun `returns Success regardless of channel result when force cache enabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
        every { preferences.isForceCache() } returns true
        coEvery { mediaChannel.syncProgress(any(), any()) } returns
          OperationResult.Error(OperationError.NetworkError)

        val result = provider.syncProgress("session-1", item, progress)

        assertInstanceOf(OperationResult.Success::class.java, result)
      }

    @Test
    fun `always syncs local cache regardless of force cache flag`() =
      runBlocking {
        val item = detailedItem("book-1")
        val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
        every { preferences.isForceCache() } returns true

        provider.syncProgress("session-1", item, progress)

        coVerify { localCacheRepository.syncProgress(item, progress) }
      }

    @Test
    fun `uses channel result when force cache disabled`() =
      runBlocking {
        val item = detailedItem("book-1")
        val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 100.0)
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.syncProgress("session-1", progress)
        } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.syncProgress("session-1", item, progress)

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class MarkAsListened {
    @Test
    fun `always updates local cache regardless of force cache flag`() =
      runBlocking {
        every { preferences.isForceCache() } returns true

        provider.markAsListened("book-1", true)

        coVerify { localCacheRepository.updateFinishedState("book-1", true) }
      }

    @Test
    fun `returns Success without calling channel when force cache enabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns true

        val result = provider.markAsListened("book-1", true)

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify(exactly = 0) { mediaChannel.updateListenedState(any(), any()) }
      }

    @Test
    fun `calls channel and returns its result when force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.updateListenedState("book-1", true)
        } returns OperationResult.Success(Unit)

        val result = provider.markAsListened("book-1", true)

        assertInstanceOf(OperationResult.Success::class.java, result)
        coVerify { mediaChannel.updateListenedState("book-1", true) }
      }

    @Test
    fun `returns Error from markAsListened when channel call fails and force cache disabled`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.updateListenedState(any(), any())
        } returns OperationResult.Error(OperationError.NetworkError)

        val result = provider.markAsListened("book-1", false)

        assertInstanceOf(OperationResult.Error::class.java, result)
        assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
      }

    @Test
    fun `passes isFinished through to local cache and channel unchanged`() =
      runBlocking {
        every { preferences.isForceCache() } returns false
        coEvery {
          mediaChannel.updateListenedState("book-1", false)
        } returns OperationResult.Success(Unit)

        provider.markAsListened("book-1", false)

        coVerify { localCacheRepository.updateFinishedState("book-1", false) }
        coVerify { mediaChannel.updateListenedState("book-1", false) }
      }
  }

  @Nested
  inner class ProvideFileUri {
    @Test
    fun `returns cached URI when local cache has it`() {
      val uri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri("book-1", "chapter-1") } returns uri

      val result = provider.provideFileUri("book-1", "chapter-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(uri, (result as OperationResult.Success).data)
    }

    @Test
    fun `returns Error when force cache enabled and no local URI`() {
      every { preferences.isForceCache() } returns true
      every { localCacheRepository.provideFileUri(any(), any()) } returns null

      val result = provider.provideFileUri("book-1", "chapter-1")

      assertInstanceOf(OperationResult.Error::class.java, result)
      assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
    }

    @Test
    fun `falls back to channel URI when force cache disabled and no local URI`() {
      val channelUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri(any(), any()) } returns null
      every { mediaChannel.provideFileUri("book-1", "chapter-1") } returns channelUri

      val result = provider.provideFileUri("book-1", "chapter-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(channelUri, (result as OperationResult.Success).data)
    }

    @Test
    fun `prefers local cache URI over channel URI when both available`() {
      val localUri = mockk<Uri>()
      val channelUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri("book-1", "file-1") } returns localUri
      every { mediaChannel.provideFileUri("book-1", "file-1") } returns channelUri

      val result = provider.provideFileUri("book-1", "file-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(localUri, (result as OperationResult.Success).data)
    }

    @Test
    fun `force cache returns cached URI when available`() {
      val localUri = mockk<Uri>()
      every { preferences.isForceCache() } returns true
      every { localCacheRepository.provideFileUri("book-1", "file-1") } returns localUri

      val result = provider.provideFileUri("book-1", "file-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals(localUri, (result as OperationResult.Success).data)
    }

    @Test
    fun `channel fallback always succeeds when force cache disabled`() {
      val channelUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri(any(), any()) } returns null
      every { mediaChannel.provideFileUri("book-1", "file-1") } returns channelUri

      val result = provider.provideFileUri("book-1", "file-1")

      assertInstanceOf(OperationResult.Success::class.java, result)
    }

    @Test
    fun `does not call channel when force cache enabled`() {
      every { preferences.isForceCache() } returns true
      every { localCacheRepository.provideFileUri(any(), any()) } returns null

      provider.provideFileUri("book-1", "file-1")

      io.mockk.verify(exactly = 0) { mediaChannel.provideFileUri(any(), any()) }
    }

    @Test
    fun `does not call channel when local cache has URI and force cache disabled`() {
      val localUri = mockk<Uri>()
      every { preferences.isForceCache() } returns false
      every { localCacheRepository.provideFileUri("book-1", "file-1") } returns localUri

      provider.provideFileUri("book-1", "file-1")

      io.mockk.verify(exactly = 0) { mediaChannel.provideFileUri(any(), any()) }
    }
  }

  @Nested
  inner class Bookmarks {
    @Test
    fun `provideBookmarks deduplicates bookmarks with same libraryItemId and totalPosition`() =
      runBlocking {
        val bm1 = bookmark(libraryItemId = "book-1", totalPosition = 100.0, createdAt = 1L)
        val bm2 = bookmark(libraryItemId = "book-1", totalPosition = 100.0, createdAt = 2L)
        coEvery { cachedBookmarkProvider.provideBookmarks("book-1") } returns listOf(bm1, bm2)

        val result = provider.provideBookmarks("book-1")

        assertEquals(1, result.size)
      }

    @Test
    fun `provideBookmarks sorts by createdAt descending`() =
      runBlocking {
        val bm1 = bookmark(libraryItemId = "book-1", totalPosition = 100.0, createdAt = 1L)
        val bm2 = bookmark(libraryItemId = "book-1", totalPosition = 200.0, createdAt = 5L)
        val bm3 = bookmark(libraryItemId = "book-1", totalPosition = 300.0, createdAt = 3L)
        coEvery { cachedBookmarkProvider.provideBookmarks("book-1") } returns listOf(bm1, bm2, bm3)

        val result = provider.provideBookmarks("book-1")

        assertEquals(listOf(200.0, 300.0, 100.0), result.map { it.totalPosition })
      }
  }

  private fun detailedItem(
    id: String = "book-1",
    chapters: List<PlayingChapter> = emptyList(),
  ) = DetailedItem(
    id = id,
    title = "Test Book",
    subtitle = null,
    author = "Author",
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = chapters,
    progress = null,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private fun recentBook(id: String) =
    RecentBook(
      id = id,
      title = "Book $id",
      subtitle = null,
      author = "Author",
      listenedPercentage = null,
      listenedLastUpdate = null,
    )

  private fun book(id: String) =
    Book(
      id = id,
      title = "Book $id",
      subtitle = null,
      series = null,
      author = "Author",
    )

  private fun List<LibraryEntry>.bookIds(): List<String> =
    mapNotNull {
      when (it) {
        is LibraryEntry.BookEntry -> it.book.id
        else -> null
      }
    }

  private fun bookmark(
    libraryItemId: String,
    totalPosition: Double,
    createdAt: Long,
  ) = Bookmark(
    libraryItemId = libraryItemId,
    title = "Bookmark",
    totalPosition = totalPosition,
    createdAt = createdAt,
    syncState = org.grakovne.lissen.domain.BookmarkSyncState.SYNCED,
  )
}
