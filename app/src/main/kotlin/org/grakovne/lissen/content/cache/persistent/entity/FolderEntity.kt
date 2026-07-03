package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Keep
@Entity(
  tableName = "folders",
)
data class FolderEntity(
  @PrimaryKey
  val id: String,
  val name: String,
  val createdAt: Long,
) : Serializable
