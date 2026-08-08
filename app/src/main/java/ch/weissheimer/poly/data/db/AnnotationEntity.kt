package ch.weissheimer.poly.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    indices = [Index("fileHash"), Index("fileUri")],
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val fileHash: String,
    val fileUri: String,
    val format: String,
    val type: String,
    val color: String,
    val startOffset: Int?,
    val endOffset: Int?,
    val quotedText: String?,
    val prefix: String?,
    val suffix: String?,
    val pageIndex: Int?,
    /** Comma-separated normalized floats. */
    val points: String?,
    val orphaned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
