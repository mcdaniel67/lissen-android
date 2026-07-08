package org.grakovne.lissen.content.folder

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.cache.persistent.dao.FolderDao
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LocalFolderRepositoryTest {
  private val folderDao = mockk<FolderDao>(relaxed = true)
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)

  private lateinit var repository: LocalFolderRepository

  @BeforeEach
  fun setup() {
    repository = LocalFolderRepository(folderDao, preferences)
  }

  @Test
  fun `clear delegates to the dao clear which wipes both tables`() =
    runTest {
      repository.clear()

      coVerify(exactly = 1) { folderDao.clear() }
    }

  @Test
  fun `folderCount delegates to the dao`() =
    runTest {
      coEvery { folderDao.folderCount() } returns 3

      assertEquals(3, repository.folderCount())
    }

  @Test
  fun `createFolder records the current host as folders host`() =
    runTest {
      every { preferences.getHost() } returns "https://server-a.example"

      repository.createFolder("My Folder", listOf(book("book-1")))

      verify(exactly = 1) { preferences.saveFoldersHost("https://server-a.example") }
    }

  @Test
  fun `createFolder does not touch folders host when there is no host`() =
    runTest {
      every { preferences.getHost() } returns null

      repository.createFolder("My Folder", listOf(book("book-1")))

      verify(exactly = 0) { preferences.saveFoldersHost(any()) }
    }

  private fun book(id: String) =
    Book(
      id = id,
      title = "Title $id",
      subtitle = null,
      author = "Author",
      series = null,
    )
}
