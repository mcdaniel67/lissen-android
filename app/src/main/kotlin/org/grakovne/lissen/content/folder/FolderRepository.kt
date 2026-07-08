package org.grakovne.lissen.content.folder

import kotlinx.coroutines.flow.Flow
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry

/**
 * User-defined folders of books.
 *
 * Storage-agnostic on purpose: the current implementation is local ([LocalFolderRepository]),
 * but the same surface can later be backed by Audiobookshelf server Collections without any
 * change to callers.
 */
interface FolderRepository {
  fun observeFolders(): Flow<List<LibraryEntry.FolderEntry>>

  suspend fun folderBooks(folderId: String): List<Book>

  /** Ids of every book that belongs to any folder, so they can be hidden from the flat library list. */
  suspend fun foldedBookIds(): Set<String>

  suspend fun createFolder(
    name: String,
    books: List<Book>,
  )

  suspend fun renameFolder(
    folderId: String,
    name: String,
  )

  suspend fun addBooks(
    folderId: String,
    books: List<Book>,
  )

  suspend fun removeBook(
    folderId: String,
    bookId: String,
  )

  suspend fun deleteFolder(folderId: String)

  /** Number of folders currently stored, regardless of how many books each holds. */
  suspend fun folderCount(): Int

  /**
   * Wipes every folder and its membership. Used when the app is pointed at a different server
   * (folder book ids are server-scoped and would be dead) or on explicit logout.
   */
  suspend fun clear()
}
