package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import java.io.Serializable

/**
 * Folder membership. Book metadata is denormalized so folders render fully offline,
 * without depending on a book being downloaded or on a network round-trip.
 */
@Keep
@Entity(
  tableName = "folder_items",
  primaryKeys = ["folderId", "bookId"],
)
data class FolderItemEntity(
  val folderId: String,
  val bookId: String,
  val title: String,
  val subtitle: String?,
  val author: String?,
  val series: String?,
  val position: Int,
) : Serializable
