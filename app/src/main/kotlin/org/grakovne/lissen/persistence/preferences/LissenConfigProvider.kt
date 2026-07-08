package org.grakovne.lissen.persistence.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.content.folder.FolderExport
import org.grakovne.lissen.content.folder.FolderRepository
import org.grakovne.lissen.domain.Book
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenConfigProvider
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val folderRepository: FolderRepository,
  ) {
    private val adapter = moshi.adapter(SettingsBackup::class.java)

    suspend fun exportConfigFile(): File? =
      withContext(Dispatchers.IO) {
        runCatching {
          val settings =
            preferences
              .exportSettings()
              .copy(folders = folderRepository.exportFolders().map { it.toBackup() })

          File(context.cacheDir, FILE_CONFIG_NAME)
            .apply { writeText(adapter.toJson(settings)) }
        }.getOrNull()
      }

    suspend fun importConfig(json: String): Boolean =
      withContext(Dispatchers.IO) {
        val backup = runCatching { adapter.fromJson(json) }.getOrNull() ?: return@withContext false

        preferences.importSettings(backup)
        backup.folders?.let { folders -> folderRepository.importFolders(folders.map { it.toExport() }) }
        true
      }

    private fun FolderExport.toBackup(): FolderBackup =
      FolderBackup(
        id = id,
        name = name,
        createdAt = createdAt,
        books =
          books.map {
            FolderBookBackup(id = it.id, title = it.title, subtitle = it.subtitle, author = it.author, series = it.series)
          },
      )

    private fun FolderBackup.toExport(): FolderExport =
      FolderExport(
        id = id,
        name = name,
        createdAt = createdAt,
        books =
          books.map {
            Book(id = it.id, subtitle = it.subtitle, series = it.series, title = it.title, author = it.author)
          },
      )

    companion object {
      private const val FILE_CONFIG_NAME = "lissen-settings.json"
    }
  }
