package ch.weissheimer.poly.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val format: String,
    val sha256: String,
    val lastOpenedAt: Long,
)
