package ch.weissheimer.poly.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {

    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC LIMIT 100")
    fun observeAll(): Flow<List<RecentFileEntity>>

    @Upsert
    suspend fun upsert(entity: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun delete(uri: String)
}
