package org.grakovne.lissen.content.cache.persistent.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.grakovne.lissen.content.cache.persistent.entity.FolderEntity
import org.grakovne.lissen.content.cache.persistent.entity.FolderItemEntity

@Dao
interface FolderDao {
  @Query("SELECT * FROM folders ORDER BY createdAt ASC")
  fun observeFolders(): Flow<List<FolderEntity>>

  @Query("SELECT * FROM folder_items WHERE folderId = :folderId ORDER BY position ASC")
  suspend fun folderItems(folderId: String): List<FolderItemEntity>

  @Query("SELECT DISTINCT bookId FROM folder_items")
  suspend fun foldedBookIds(): List<String>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertFolder(folder: FolderEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertItems(items: List<FolderItemEntity>)

  @Query("UPDATE folders SET name = :name WHERE id = :folderId")
  suspend fun renameFolder(
    folderId: String,
    name: String,
  )

  @Query("DELETE FROM folders WHERE id = :folderId")
  suspend fun deleteFolder(folderId: String)

  @Query("DELETE FROM folder_items WHERE folderId = :folderId")
  suspend fun deleteItems(folderId: String)

  @Query("DELETE FROM folder_items WHERE folderId = :folderId AND bookId = :bookId")
  suspend fun deleteItem(
    folderId: String,
    bookId: String,
  )

  @Query("SELECT COALESCE(MAX(position), -1) FROM folder_items WHERE folderId = :folderId")
  suspend fun maxPosition(folderId: String): Int

  @Query("SELECT COUNT(*) FROM folders")
  suspend fun folderCount(): Int

  @Query("DELETE FROM folder_items")
  suspend fun deleteAllItems()

  @Query("DELETE FROM folders")
  suspend fun deleteAllFolders()

  @Transaction
  suspend fun replaceFolder(
    folder: FolderEntity,
    items: List<FolderItemEntity>,
  ) {
    upsertFolder(folder)
    deleteItems(folder.id)
    upsertItems(items)
  }

  @Transaction
  suspend fun clear() {
    deleteAllItems()
    deleteAllFolders()
  }
}
