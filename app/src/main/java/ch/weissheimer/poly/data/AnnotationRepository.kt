package ch.weissheimer.poly.data

import ch.weissheimer.poly.annotation.Annotation
import ch.weissheimer.poly.annotation.AnnotationColor
import ch.weissheimer.poly.annotation.AnnotationType
import ch.weissheimer.poly.annotation.ReAnchor
import ch.weissheimer.poly.annotation.TextAnchor
import ch.weissheimer.poly.core.DocumentFormat
import ch.weissheimer.poly.data.db.AnnotationDao
import ch.weissheimer.poly.data.db.AnnotationEntity

class ReAnchorOutcome(
    val anchored: List<Annotation>,
    val orphanedCount: Int,
)

class AnnotationRepository(private val dao: AnnotationDao) {

    fun observeAnnotatedHashes() = dao.observeAnnotatedHashes()

    suspend fun save(annotation: Annotation) = dao.upsert(annotation.toEntity(orphaned = false))

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun loadFor(info: DocumentInfo, text: String?): ReAnchorOutcome =
        loadFor(info.uri.toString(), info.sha256, text)

    /**
     * Loads displayable annotations for a document. Exact hash match wins;
     * otherwise earlier annotations on the same URI are re-anchored against
     * [text] (text highlights) or re-bound directly (geometric shapes, their
     * normalized coordinates stay valid), persisted under the new hash.
     * Unresolvable text highlights are stored as orphaned.
     */
    suspend fun loadFor(fileUri: String, fileHash: String, text: String?): ReAnchorOutcome {
        val byHash = dao.byHash(fileHash)
        if (byHash.isNotEmpty()) {
            return ReAnchorOutcome(byHash.mapNotNull { it.toDomain() }, 0)
        }

        val previous = dao.byUri(fileUri)
            .filter { it.fileHash != fileHash && !it.orphaned }
        if (previous.isEmpty()) return ReAnchorOutcome(emptyList(), 0)

        val anchored = mutableListOf<Annotation>()
        val updatedEntities = mutableListOf<AnnotationEntity>()
        var orphaned = 0

        for (entity in previous) {
            val domain = entity.toDomain() ?: continue
            if (domain.type == AnnotationType.TEXT_HIGHLIGHT) {
                val anchor = domain.anchor
                val rebound = if (anchor != null && text != null) {
                    ReAnchor.anchor(anchor, text)
                } else null
                if (rebound != null) {
                    val updated = domain.copy(
                        fileHash = fileHash,
                        anchor = rebound,
                        updatedAt = System.currentTimeMillis(),
                    )
                    anchored.add(updated)
                    updatedEntities.add(updated.toEntity(orphaned = false))
                } else {
                    orphaned++
                    updatedEntities.add(entity.copy(orphaned = true))
                }
            } else {
                val updated = domain.copy(
                    fileHash = fileHash,
                    updatedAt = System.currentTimeMillis(),
                )
                anchored.add(updated)
                updatedEntities.add(updated.toEntity(orphaned = false))
            }
        }
        dao.upsertAll(updatedEntities)
        return ReAnchorOutcome(anchored, orphaned)
    }
}

private fun Annotation.toEntity(orphaned: Boolean) = AnnotationEntity(
    id = id,
    fileHash = fileHash,
    fileUri = fileUri,
    format = format.name,
    type = type.name,
    color = color.name,
    startOffset = anchor?.startOffset,
    endOffset = anchor?.endOffset,
    quotedText = anchor?.quotedText,
    prefix = anchor?.prefix,
    suffix = anchor?.suffix,
    pageIndex = pageIndex,
    points = points.takeIf { it.isNotEmpty() }?.joinToString(","),
    orphaned = orphaned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AnnotationEntity.toDomain(): Annotation? {
    val annotationType = runCatching { AnnotationType.valueOf(type) }.getOrNull() ?: return null
    val annotationColor = runCatching { AnnotationColor.valueOf(color) }.getOrNull()
        ?: AnnotationColor.YELLOW
    val documentFormat = runCatching { DocumentFormat.valueOf(format) }.getOrNull()
        ?: DocumentFormat.UNKNOWN
    val anchor = if (startOffset != null && endOffset != null && quotedText != null) {
        TextAnchor(startOffset, endOffset, quotedText, prefix.orEmpty(), suffix.orEmpty())
    } else null
    return Annotation(
        id = id,
        fileHash = fileHash,
        fileUri = fileUri,
        format = documentFormat,
        type = annotationType,
        color = annotationColor,
        anchor = anchor,
        pageIndex = pageIndex,
        points = points?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: emptyList(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
