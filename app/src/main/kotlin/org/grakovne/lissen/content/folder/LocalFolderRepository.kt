package org.grakovne.lissen.content.folder

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.grakovne.lissen.content.cache.persistent.dao.FolderDao
import org.grakovne.lissen.content.cache.persistent.entity.FolderEntity
import org.grakovne.lissen.content.cache.persistent.entity.FolderItemEntity
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFolderRepository
  @Inject
  constructor(
    private val folderDao: FolderDao,
  ) : FolderRepository {
    override fun observeFolders(): Flow<List<LibraryEntry.FolderEntry>> =
      folderDao
        .observeFolders()
        .map { folders ->
          folders.map { folder ->
            val items = folderDao.folderItems(folder.id)
            LibraryEntry.FolderEntry(
              id = folder.id,
              name = folder.name,
              bookCount = items.size,
              coverItemIds = items.take(COVER_ITEMS).map { it.bookId },
            )
          }
        }

    override suspend fun folderBooks(folderId: String): List<Book> =
      folderDao
        .folderItems(folderId)
        .map { it.toBook() }

    override suspend fun createFolder(
      name: String,
      books: List<Book>,
    ) {
      val folderId = UUID.randomUUID().toString()
      val folder = FolderEntity(id = folderId, name = name.trim(), createdAt = System.currentTimeMillis())
      folderDao.replaceFolder(folder, books.toItems(folderId, startPosition = 0))
    }

    override suspend fun renameFolder(
      folderId: String,
      name: String,
    ) = folderDao.renameFolder(folderId, name.trim())

    override suspend fun addBooks(
      folderId: String,
      books: List<Book>,
    ) {
      val start = folderDao.maxPosition(folderId) + 1
      folderDao.upsertItems(books.toItems(folderId, startPosition = start))
    }

    override suspend fun removeBook(
      folderId: String,
      bookId: String,
    ) = folderDao.deleteItem(folderId, bookId)

    override suspend fun deleteFolder(folderId: String) {
      folderDao.deleteItems(folderId)
      folderDao.deleteFolder(folderId)
    }

    private fun List<Book>.toItems(
      folderId: String,
      startPosition: Int,
    ): List<FolderItemEntity> =
      mapIndexed { index, book ->
        FolderItemEntity(
          folderId = folderId,
          bookId = book.id,
          title = book.title,
          subtitle = book.subtitle,
          author = book.author,
          series = book.series,
          position = startPosition + index,
        )
      }

    private fun FolderItemEntity.toBook(): Book =
      Book(
        id = bookId,
        subtitle = subtitle,
        series = series,
        title = title,
        author = author,
      )

    companion object {
      private const val COVER_ITEMS = 3
    }
  }
