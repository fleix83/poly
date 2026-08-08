package ch.weissheimer.poly.data

import ch.weissheimer.poly.annotation.Annotation
import ch.weissheimer.poly.annotation.AnnotationColor
import ch.weissheimer.poly.annotation.AnnotationType
import ch.weissheimer.poly.annotation.ReAnchor
import ch.weissheimer.poly.core.DocumentFormat
import ch.weissheimer.poly.data.db.AnnotationDao
import ch.weissheimer.poly.data.db.AnnotationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAnnotationDao : AnnotationDao {
    val store = LinkedHashMap<String, AnnotationEntity>()

    override suspend fun byHash(fileHash: String) =
        store.values.filter { it.fileHash == fileHash && !it.orphaned }.sortedBy { it.createdAt }

    override suspend fun byUri(fileUri: String) =
        store.values.filter { it.fileUri == fileUri }.sortedBy { it.createdAt }

    override suspend fun upsert(entity: AnnotationEntity) {
        store[entity.id] = entity
    }

    override suspend fun upsertAll(entities: List<AnnotationEntity>) {
        entities.forEach { store[it.id] = it }
    }

    override suspend fun delete(id: String) {
        store.remove(id)
    }

    override suspend fun countByHash(fileHash: String) =
        store.values.count { it.fileHash == fileHash && !it.orphaned }

    override fun observeAnnotatedHashes(): Flow<List<String>> =
        flowOf(store.values.filter { !it.orphaned }.map { it.fileHash }.distinct())
}

class AnnotationRepositoryTest {

    private val text = "Der schnelle braune Fuchs springt über den faulen Hund."
    private val uri = "content://test/doc.txt"

    private fun highlight(id: String, hash: String, quoted: String): Annotation {
        val start = text.indexOf(quoted)
        return Annotation(
            id = id,
            fileHash = hash,
            fileUri = uri,
            format = DocumentFormat.TXT,
            type = AnnotationType.TEXT_HIGHLIGHT,
            color = AnnotationColor.GREEN,
            anchor = ReAnchor.contextFor(text, start, start + quoted.length),
            pageIndex = null,
            points = emptyList(),
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    @Test
    fun `save and load round-trip by hash`() = runBlocking {
        val dao = FakeAnnotationDao()
        val repository = AnnotationRepository(dao)
        repository.save(highlight("a", "hash1", "braune Fuchs"))

        val outcome = repository.loadFor(uri, "hash1", text)
        assertEquals(1, outcome.anchored.size)
        assertEquals(0, outcome.orphanedCount)
        val anchor = outcome.anchored.first().anchor!!
        assertEquals("braune Fuchs", anchor.quotedText)
        assertEquals(AnnotationColor.GREEN, outcome.anchored.first().color)
    }

    @Test
    fun `changed file re-anchors and persists under new hash`() = runBlocking {
        val dao = FakeAnnotationDao()
        val repository = AnnotationRepository(dao)
        repository.save(highlight("a", "hash1", "braune Fuchs"))

        val changedText = "EINLEITUNG.\n\n$text"
        val outcome = repository.loadFor(uri, "hash2", changedText)
        assertEquals(1, outcome.anchored.size)
        assertEquals(
            changedText.indexOf("braune Fuchs"),
            outcome.anchored.first().anchor!!.startOffset,
        )
        // Persisted: a second load by the new hash succeeds directly.
        val second = repository.loadFor(uri, "hash2", changedText)
        assertEquals(1, second.anchored.size)
    }

    @Test
    fun `missing passage orphans the annotation`() = runBlocking {
        val dao = FakeAnnotationDao()
        val repository = AnnotationRepository(dao)
        repository.save(highlight("a", "hash1", "braune Fuchs"))

        val outcome = repository.loadFor(uri, "hash2", "Völlig anderer Inhalt.")
        assertEquals(0, outcome.anchored.size)
        assertEquals(1, outcome.orphanedCount)
        assertTrue(dao.store.getValue("a").orphaned)
    }

    @Test
    fun `geometric annotations re-bind without text`() = runBlocking {
        val dao = FakeAnnotationDao()
        val repository = AnnotationRepository(dao)
        val rect = Annotation(
            id = "r",
            fileHash = "hash1",
            fileUri = uri,
            format = DocumentFormat.PDF,
            type = AnnotationType.RECT,
            color = AnnotationColor.YELLOW,
            anchor = null,
            pageIndex = 3,
            points = listOf(0.1f, 0.2f, 0.5f, 0.6f),
            createdAt = 1L,
            updatedAt = 1L,
        )
        repository.save(rect)

        val outcome = repository.loadFor(uri, "hash2", null)
        assertEquals(1, outcome.anchored.size)
        val rebound = outcome.anchored.first()
        assertEquals(listOf(0.1f, 0.2f, 0.5f, 0.6f), rebound.points)
        assertEquals(3, rebound.pageIndex)
        assertEquals("hash2", rebound.fileHash)
    }
}
