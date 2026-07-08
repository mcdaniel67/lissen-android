package org.grakovne.lissen.content.folder

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.cache.persistent.dao.FolderDao
import org.grakovne.lissen.content.cache.persistent.entity.FolderEntity
import org.grakovne.lissen.content.cache.persistent.entity.FolderItemEntity
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

  @Test
  fun `exportFolders reads every folder with its books`() =
    runTest {
      coEvery { folderDao.folders() } returns
        listOf(FolderEntity(id = "f1", name = "Sci-Fi", createdAt = 42L))
      coEvery { folderDao.folderItems("f1") } returns
        listOf(
          FolderItemEntity(
            folderId = "f1",
            bookId = "b1",
            title = "Title b1",
            subtitle = null,
            author = "Author",
            series = null,
            position = 0,
          ),
        )

      val export = repository.exportFolders()

      assertEquals(1, export.size)
      assertEquals("f1", export[0].id)
      assertEquals("Sci-Fi", export[0].name)
      assertEquals(42L, export[0].createdAt)
      assertEquals(listOf("b1"), export[0].books.map { it.id })
    }

  @Test
  fun `importFolders replaces each folder by id preserving its id and createdAt`() =
    runTest {
      val snapshot =
        FolderExport(id = "f1", name = "Sci-Fi", createdAt = 42L, books = listOf(book("b1"), book("b2")))

      repository.importFolders(listOf(snapshot))

      coVerify(exactly = 1) {
        folderDao.replaceFolder(
          match { it.id == "f1" && it.name == "Sci-Fi" && it.createdAt == 42L },
          match { items -> items.map { it.bookId } == listOf("b1", "b2") && items.map { it.position } == listOf(0, 1) },
        )
      }
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
