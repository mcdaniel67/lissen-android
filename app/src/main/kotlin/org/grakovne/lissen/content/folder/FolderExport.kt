package org.grakovne.lissen.content.folder

import org.grakovne.lissen.domain.Book

/**
 * Full snapshot of a single folder and its books, used to carry folders through the configuration
 * backup/restore flow. Storage-neutral so the backup layer never touches Room entities directly.
 */
data class FolderExport(
  val id: String,
  val name: String,
  val createdAt: Long,
  val books: List<Book>,
)
