package ch.weissheimer.poly.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecentFileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PolyDatabase : RoomDatabase() {
    abstract fun recentFileDao(): RecentFileDao
}
