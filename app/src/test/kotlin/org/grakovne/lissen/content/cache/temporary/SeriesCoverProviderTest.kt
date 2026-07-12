package org.grakovne.lissen.content.cache.temporary

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.common.SeriesCoverComposer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SeriesCoverProviderTest {
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val composer = mockk<SeriesCoverComposer>()
  private val properties = mockk<ShortTermCacheStorageProperties>()
  private val provider = SeriesCoverProvider(mediaProvider, composer, properties)

  @Test
  fun `returns the cached composite without fetching or recomposing`(
    @TempDir dir: File,
  ) = runBlocking {
    val cached = File(dir, "cached").apply { writeText("png") }
    every { properties.provideSeriesCoverPath(any()) } returns cached

    val result = provider.provideSeriesCover("ser-1", listOf("b1", "b2"))

    assertInstanceOf(OperationResult.Success::class.java, result)
    assertEquals(cached, (result as OperationResult.Success).data)
    coVerify(exactly = 0) { mediaProvider.fetchBookCover(any()) }
    verify(exactly = 0) { composer.compose(any()) }
  }

  @Test
  fun `replaces an empty cached composite`(
    @TempDir dir: File,
  ) = runBlocking {
    val cached = File(dir, "cached").apply { createNewFile() }
    every { properties.provideSeriesCoverPath(any()) } returns cached
    coEvery { mediaProvider.fetchBookCover("b1") } returns OperationResult.Success(File(dir, "b1"))
    every { composer.compose(any()) } returns Buffer().apply { writeUtf8("PNG") }

    val result = provider.provideSeriesCover("ser-1", listOf("b1"))

    assertInstanceOf(OperationResult.Success::class.java, result)
    assertEquals(cached, (result as OperationResult.Success).data)
    assertEquals("PNG", cached.readText())
    coVerify(exactly = 1) { mediaProvider.fetchBookCover("b1") }
    verify(exactly = 1) { composer.compose(any()) }
  }

  @Test
  fun `composes four covers once and reuses the persisted composite`(
    @TempDir dir: File,
  ) = runBlocking {
    val dest = File(dir, "composed")
    every { properties.provideSeriesCoverPath(any()) } returns dest
    (1..4).forEach { index ->
      coEvery { mediaProvider.fetchBookCover("b$index") } returns OperationResult.Success(File(dir, "b$index"))
    }
    every { composer.compose(any()) } returns Buffer().apply { writeUtf8("PNGBYTES") }

    val first = provider.provideSeriesCover("ser-1", listOf("b1", "b2", "b3", "b4"))
    val second = provider.provideSeriesCover("ser-1", listOf("b1", "b2", "b3", "b4"))

    assertInstanceOf(OperationResult.Success::class.java, first)
    assertInstanceOf(OperationResult.Success::class.java, second)
    assertEquals(dest, (first as OperationResult.Success).data)
    assertEquals(dest, (second as OperationResult.Success).data)
    assertTrue(dest.exists())
    assertEquals("PNGBYTES", dest.readText())
    (1..4).forEach { index ->
      coVerify(exactly = 1) { mediaProvider.fetchBookCover("b$index") }
    }
    verify(exactly = 1) { composer.compose(any()) }
  }

  @Test
  fun `persists and reuses a four-cover composite when one source is unavailable`(
    @TempDir dir: File,
  ) = runBlocking {
    val dest = File(dir, "composed")
    every { properties.provideSeriesCoverPath(any()) } returns dest
    coEvery { mediaProvider.fetchBookCover("b1") } returns OperationResult.Error(OperationError.NetworkError)
    coEvery { mediaProvider.fetchBookCover("b2") } returns OperationResult.Success(File(dir, "b2"))
    coEvery { mediaProvider.fetchBookCover("b3") } returns OperationResult.Success(File(dir, "b3"))
    coEvery { mediaProvider.fetchBookCover("b4") } returns OperationResult.Success(File(dir, "b4"))
    every { composer.compose(any()) } returns Buffer().apply { writeUtf8("PNG") }

    val first = provider.provideSeriesCover("ser-1", listOf("b1", "b2", "b3", "b4"))
    val second = provider.provideSeriesCover("ser-1", listOf("b1", "b2", "b3", "b4"))

    assertInstanceOf(OperationResult.Success::class.java, first)
    assertInstanceOf(OperationResult.Success::class.java, second)
    assertEquals(dest, (first as OperationResult.Success).data)
    assertEquals(dest, (second as OperationResult.Success).data)
    assertTrue(dest.exists())
    coVerify(exactly = 1) { mediaProvider.fetchBookCover("b1") }
    coVerify(exactly = 1) { mediaProvider.fetchBookCover("b2") }
    coVerify(exactly = 1) { mediaProvider.fetchBookCover("b3") }
    coVerify(exactly = 1) { mediaProvider.fetchBookCover("b4") }
    verify(exactly = 1) { composer.compose(match { it.size == 3 }) }
  }

  @Test
  fun `changing one of four cover ids invalidates the persisted composite`(
    @TempDir dir: File,
  ) = runBlocking {
    every { properties.provideSeriesCoverPath(any()) } answers { File(dir, firstArg<String>()) }
    coEvery { mediaProvider.fetchBookCover(any()) } answers {
      OperationResult.Success(File(dir, firstArg<String>()))
    }
    every { composer.compose(any()) } answers { Buffer().apply { writeUtf8("PNG") } }

    val first = provider.provideSeriesCover("ser-1", listOf("b1", "b2", "b3", "b4"))
    val changed = provider.provideSeriesCover("ser-1", listOf("b1", "b2", "b3", "b5"))

    assertInstanceOf(OperationResult.Success::class.java, first)
    assertInstanceOf(OperationResult.Success::class.java, changed)
    assertNotEquals(
      (first as OperationResult.Success).data,
      (changed as OperationResult.Success).data,
    )
    verify(exactly = 2) { composer.compose(match { it.size == 4 }) }
  }

  @Test
  fun `returns error when nothing can be composed`(
    @TempDir dir: File,
  ) = runBlocking {
    every { properties.provideSeriesCoverPath(any()) } returns File(dir, "missing")
    coEvery { mediaProvider.fetchBookCover(any()) } returns OperationResult.Error(OperationError.NetworkError)
    every { composer.compose(any()) } returns null

    val result = provider.provideSeriesCover("ser-1", listOf("b1"))

    assertInstanceOf(OperationResult.Error::class.java, result)
    assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
  }
}
