package ch.weissheimer.poly.annotation

import androidx.compose.ui.graphics.Color
import ch.weissheimer.poly.core.DocumentFormat

enum class AnnotationColor(val color: Color) {
    YELLOW(Color(0xFFFFEB3B)),
    GREEN(Color(0xFF81C784)),
    BLUE(Color(0xFF64B5F6)),
    RED(Color(0xFFE57373)),
}

enum class AnnotationType { TEXT_HIGHLIGHT, RECT, FREEHAND }

enum class AnnotationTool { RECT, FREEHAND }

/** Robust re-anchoring data for text highlights (PRD 9). */
data class TextAnchor(
    val startOffset: Int,
    val endOffset: Int,
    val quotedText: String,
    val prefix: String,
    val suffix: String,
)

data class Annotation(
    val id: String,
    val fileHash: String,
    val fileUri: String,
    val format: DocumentFormat,
    val type: AnnotationType,
    val color: AnnotationColor,
    val anchor: TextAnchor?,
    val pageIndex: Int?,
    /** Normalized [0..1] coordinates: RECT = [x0,y0,x1,y1], FREEHAND = [x0,y0,x1,y1,…]. */
    val points: List<Float>,
    val createdAt: Long,
    val updatedAt: Long,
)
