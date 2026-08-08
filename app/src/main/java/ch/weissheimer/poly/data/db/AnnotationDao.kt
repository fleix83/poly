package ch.weissheimer.poly.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM annotations WHERE fileHash = :fileHash AND orphaned = 0 ORDER BY createdAt")
    suspend fun byHash(fileHash: String): List<AnnotationEntity>

    @Query("SELECT * FROM annotations WHERE fileUri = :fileUri ORDER BY createdAt")
    suspend fun byUri(fileUri: String): List<AnnotationEntity>

    @Upsert
    suspend fun upsert(entity: AnnotationEntity)

    @Upsert
    suspend fun upsertAll(entities: List<AnnotationEntity>)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM annotations WHERE fileHash = :fileHash AND orphaned = 0")
    suspend fun countByHash(fileHash: String): Int

    @Query("SELECT DISTINCT fileHash FROM annotations WHERE orphaned = 0")
    fun observeAnnotatedHashes(): kotlinx.coroutines.flow.Flow<List<String>>
}
