package org.grakovne.lissen.ui.components

import coil3.Extras
import coil3.request.Options
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class AuthorCoverFetcherTest {
  private val localCacheRepository = mockk<LocalCacheRepository>()
  private val mediaChannel = mockk<LissenMediaProvider>()

  @Test
  fun `keyer uses stable author id rather than display name`() {
    val options = mockk<Options>(relaxed = true)
    val keyer = AuthorCoverKeyer()

    assertEquals("author:a1", keyer.key(AuthorCoverKey("a1", "Ursula K Le Guin"), options))
    assertEquals("author:a1", keyer.key(AuthorCoverKey("a1", "Ursula Le Guin"), options))
  }

  @Test
  fun `online fetch resolves author cover by id`() =
    runBlocking {
      val options = options(localOnly = false)
      coEvery { mediaChannel.fetchAuthorCover("a1") } returns OperationResult.Success(File("online-cover"))

      AuthorCoverFetcher(localCacheRepository, mediaChannel, "a1", "Author Name", options).fetch()

      coVerify(exactly = 1) { mediaChannel.fetchAuthorCover("a1") }
      verify(exactly = 0) { localCacheRepository.fetchAuthorCover(any()) }
    }

  @Test
  fun `local-only fetch resolves author cover by name`() =
    runBlocking {
      val options = options(localOnly = true)
      every { localCacheRepository.fetchAuthorCover("Author Name") } returns OperationResult.Success(File("local-cover"))

      AuthorCoverFetcher(localCacheRepository, mediaChannel, "a1", "Author Name", options).fetch()

      verify(exactly = 1) { localCacheRepository.fetchAuthorCover("Author Name") }
      coVerify(exactly = 0) { mediaChannel.fetchAuthorCover(any()) }
    }

  private fun options(localOnly: Boolean): Options =
    mockk<Options> {
      every { extras } returns
        Extras
          .Builder()
          .set(ImageFetcher.LocalOnlyKey, localOnly)
          .build()
    }
}
