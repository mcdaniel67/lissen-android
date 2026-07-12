package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.persistent.api.CachedBookRepository
import org.grakovne.lissen.content.cache.persistent.api.CachedLibraryRepository
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContentCachingManagerTest {
  private val context = mockk<Context>(relaxed = true)
  private val bookRepository = mockk<CachedBookRepository>(relaxed = true)
  private val libraryRepository = mockk<CachedLibraryRepository>(relaxed = true)
  private val properties = mockk<OfflineBookStorageProperties>()
  private val channel = mockk<MediaChannel>()

  private val manager =
    ContentCachingManager(
      context = context,
      bookRepository = bookRepository,
      libraryRepository = libraryRepository,
      properties = properties,
    )

  @Test
  fun `caches a fetched cover and reports completion`(
    @TempDir dir: File,
  ) = runBlocking {
    val destination = dir.resolve("book/cover.img")
    destination.parentFile?.mkdirs()
    destination.writeText("old-cover")
    every { properties.provideBookCoverPath("book") } returns destination
    coEvery { channel.fetchBookCover("book") } returns
      OperationResult.Success(Buffer().apply { writeUtf8("cover") })

    val result = manager.cacheBookCover(book(), channel)

    assertEquals(CacheStatus.Completed, result.status)
    assertTrue(destination.exists())
    assertEquals("cover", destination.readText())
  }

  @Test
  fun `reports an error when the cover fetch fails`(
    @TempDir dir: File,
  ) = runBlocking {
    val destination = dir.resolve("book/cover.img")
    destination.parentFile?.mkdirs()
    destination.writeText("existing-cover")
    every { properties.provideBookCoverPath("book") } returns destination
    coEvery { channel.fetchBookCover("book") } returns OperationResult.Error(OperationError.NetworkError)

    val result = manager.cacheBookCover(book(), channel)

    assertEquals(CacheStatus.Error, result.status)
    assertEquals("existing-cover", destination.readText())
    assertFalse(dir.resolve("book/cover.img.tmp").exists())
  }

  @Test
  fun `reports an error when the cover cannot be written`(
    @TempDir dir: File,
  ) = runBlocking {
    val nonDirectory = dir.resolve("not-a-directory").apply { writeText("file") }
    val destination = nonDirectory.resolve("cover.img")
    every { properties.provideBookCoverPath("book") } returns destination
    coEvery { channel.fetchBookCover("book") } returns
      OperationResult.Success(Buffer().apply { writeUtf8("cover") })

    val result = manager.cacheBookCover(book(), channel)

    assertEquals(CacheStatus.Error, result.status)
    assertFalse(destination.exists())
    assertFalse(nonDirectory.resolve("cover.img.tmp").exists())
  }

  private fun book() =
    DetailedItem(
      id = "book",
      title = "Book",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      progress = null,
      libraryId = "library",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
      chapters = emptyList(),
    )
}
