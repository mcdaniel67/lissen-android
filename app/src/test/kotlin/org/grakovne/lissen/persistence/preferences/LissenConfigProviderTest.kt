package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.content.folder.FolderExport
import org.grakovne.lissen.content.folder.FolderRepository
import org.grakovne.lissen.domain.Book
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LissenConfigProviderTest {
  private val context = mockk<Context>(relaxed = true)
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val folderRepository = mockk<FolderRepository>(relaxed = true)
  private lateinit var provider: LissenConfigProvider

  @BeforeEach
  fun setup() {
    coEvery { folderRepository.exportFolders() } returns emptyList()
    provider = LissenConfigProvider(context, preferences, folderRepository)
  }

  private fun book(id: String) = Book(id = id, subtitle = "Sub $id", series = "Series", title = "Title $id", author = "Author")

  @Nested
  inner class Export {
    @Test
    fun `exportConfigFile writes serialized settings to cache`(
      @TempDir cacheDir: File,
    ) = runTest {
      every { context.cacheDir } returns cacheDir
      every { preferences.exportSettings() } returns SettingsBackup(colorScheme = "DARK", playbackSpeed = 1.5f)

      val file = provider.exportConfigFile()

      assertNotNull(file)
      val json = file!!.readText()
      assertTrue(json.contains("\"colorScheme\":\"DARK\""))
      assertTrue(json.contains("\"playbackSpeed\":1.5"))
    }

    @Test
    fun `exportConfigFile embeds folders from the repository`(
      @TempDir cacheDir: File,
    ) = runTest {
      every { context.cacheDir } returns cacheDir
      every { preferences.exportSettings() } returns SettingsBackup()
      coEvery { folderRepository.exportFolders() } returns
        listOf(FolderExport(id = "f1", name = "Sci-Fi", createdAt = 42L, books = listOf(book("b1"))))

      val json = provider.exportConfigFile()!!.readText()

      assertTrue(json.contains("\"id\":\"f1\""))
      assertTrue(json.contains("\"name\":\"Sci-Fi\""))
      assertTrue(json.contains("\"createdAt\":42"))
      assertTrue(json.contains("\"id\":\"b1\""))
    }
  }

  @Nested
  inner class Import {
    @Test
    fun `importConfig parses valid json and imports it`() =
      runTest {
        val result = provider.importConfig("""{"schemaVersion":2,"colorScheme":"DARK"}""")

        assertTrue(result)
        verify { preferences.importSettings(match { it.colorScheme == "DARK" }) }
      }

    @Test
    fun `importConfig restores folders into the repository`() =
      runTest {
        val json =
          """
          {"schemaVersion":2,"folders":[{"id":"f1","name":"Sci-Fi","createdAt":42,
          "books":[{"id":"b1","title":"Title b1","subtitle":"Sub b1","author":"Author","series":"Series"}]}]}
          """.trimIndent()

        val result = provider.importConfig(json)

        assertTrue(result)
        coVerify {
          folderRepository.importFolders(
            match { folders ->
              folders.size == 1 &&
                folders[0].id == "f1" &&
                folders[0].name == "Sci-Fi" &&
                folders[0].createdAt == 42L &&
                folders[0].books.map { it.id } == listOf("b1")
            },
          )
        }
      }

    @Test
    fun `importConfig accepts an upstream backup without a folders key`() =
      runTest {
        val result = provider.importConfig("""{"schemaVersion":1,"colorScheme":"DARK"}""")

        assertTrue(result)
        verify { preferences.importSettings(any()) }
        coVerify(exactly = 0) { folderRepository.importFolders(any()) }
      }

    @Test
    fun `importConfig returns false for malformed json`() =
      runTest {
        val result = provider.importConfig("not valid json")

        assertFalse(result)
        verify(exactly = 0) { preferences.importSettings(any()) }
        coVerify(exactly = 0) { folderRepository.importFolders(any()) }
      }

    @Test
    fun `importConfig returns false for json that does not match the schema`() =
      runTest {
        val result = provider.importConfig("""{"schemaVersion":"not-a-number"}""")

        assertFalse(result)
        verify(exactly = 0) { preferences.importSettings(any()) }
      }

    @Test
    fun `importConfig returns false for empty string`() =
      runTest {
        val result = provider.importConfig("")

        assertFalse(result)
        verify(exactly = 0) { preferences.importSettings(any()) }
      }
  }
}
