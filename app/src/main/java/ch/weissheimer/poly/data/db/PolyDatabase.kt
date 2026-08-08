package ch.weissheimer.poly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecentFileEntity::class, AnnotationEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PolyDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
    abstract fun annotationDao(): AnnotationDao
}
