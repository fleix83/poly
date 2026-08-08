package ch.weissheimer.poly.data

import ch.weissheimer.poly.data.db.RecentFileDao
import ch.weissheimer.poly.data.db.RecentFileEntity
import kotlinx.coroutines.flow.Flow

class RecentsRepository(private val dao: RecentFileDao) {

    fun observeRecents(): Flow<List<RecentFileEntity>> = dao.observeAll()

    suspend fun record(info: DocumentInfo) {
        dao.upsert(
            RecentFileEntity(
                uri = info.uri.toString(),
                displayName = info.displayName,
                sizeBytes = info.sizeBytes,
                mimeType = info.mimeType,
                format = info.format.name,
                sha256 = info.sha256,
                lastOpenedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun remove(uri: String) = dao.delete(uri)
}
