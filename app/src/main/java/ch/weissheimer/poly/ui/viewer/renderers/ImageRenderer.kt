package ch.weissheimer.poly.ui.viewer.renderers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import ch.weissheimer.poly.annotation.Annotation
import ch.weissheimer.poly.annotation.AnnotationTool
import ch.weissheimer.poly.annotation.AnnotationType
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.ViewerState
import ch.weissheimer.poly.ui.viewer.annotationGestures
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.abs
import kotlin.math.hypot

class ImageRenderer : DocumentRenderer {

    override val capabilities = RendererCapabilities(zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        val session = state.annotations
        LaunchedEffect(Unit) { session.load(null) }

        val zoomableState = rememberZoomableState()
        val imageState = rememberZoomableImageState(zoomableState)

        // Live preview while drawing (normalized image coordinates).
        var previewPoints by remember { mutableStateOf<List<Float>?>(null) }
        var previewTool by remember { mutableStateOf(AnnotationTool.FREEHAND) }
        var dragStart by remember { mutableStateOf<Offset?>(null) }

        fun imageBounds(): Rect? =
            zoomableState.transformedContentBounds.takeIf { it.width > 0f && it.height > 0f }

        fun normalize(position: Offset): Pair<Float, Float>? {
            val bounds = imageBounds() ?: return null
            return Pair(
                ((position.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
                ((position.y - bounds.top) / bounds.height).coerceIn(0f, 1f),
            )
        }

        Box(
            modifier
                .fillMaxSize()
                .annotationGestures(
                    enabled = session.modeActive,
                    onTap = { position ->
                        val normalized = normalize(position) ?: return@annotationGestures
                        session.editTarget = session.annotations.lastOrNull { annotation ->
                            hitTest(annotation, normalized.first, normalized.second)
                        }
                    },
                    onDragStart = { position ->
                        dragStart = position
                        previewTool = session.activeTool
                        val normalized = normalize(position)
                        previewPoints = if (normalized != null) {
                            listOf(normalized.first, normalized.second)
                        } else null
                    },
                    onDrag = { position ->
                        val start = dragStart ?: return@annotationGestures
                        val normalized = normalize(position) ?: return@annotationGestures
                        when (previewTool) {
                            AnnotationTool.RECT -> {
                                val startNorm = normalize(start) ?: return@annotationGestures
                                previewPoints = listOf(
                                    minOf(startNorm.first, normalized.first),
                                    minOf(startNorm.second, normalized.second),
                                    maxOf(startNorm.first, normalized.first),
                                    maxOf(startNorm.second, normalized.second),
                                )
                            }
                            AnnotationTool.FREEHAND -> {
                                val points = previewPoints ?: return@annotationGestures
                                val lastX = points[points.size - 2]
                                val lastY = points[points.size - 1]
                                if (abs(normalized.first - lastX) > 0.003f ||
                                    abs(normalized.second - lastY) > 0.003f
                                ) {
                                    previewPoints = points + listOf(normalized.first, normalized.second)
                                }
                            }
                        }
                    },
                    onDragEnd = { cancelled ->
                        val points = previewPoints
                        if (!cancelled && points != null) {
                            when (previewTool) {
                                AnnotationTool.RECT -> if (points.size == 4 &&
                                    points[2] - points[0] > 0.01f && points[3] - points[1] > 0.01f
                                ) {
                                    session.add(session.newShape(AnnotationType.RECT, points))
                                }
                                AnnotationTool.FREEHAND -> if (points.size >= 6) {
                                    session.add(session.newShape(AnnotationType.FREEHAND, points))
                                }
                            }
                        }
                        previewPoints = null
                        dragStart = null
                    },
                ),
        ) {
            ZoomableAsyncImage(
                model = state.document.uri,
                contentDescription = state.document.displayName,
                modifier = Modifier.fillMaxSize(),
                state = imageState,
                gesturesEnabled = !session.modeActive,
                onClick = { state.onContentTap() },
            )
            AnnotationOverlay(
                zoomableState = zoomableState,
                annotations = session.annotations,
                previewPoints = previewPoints,
                previewTool = previewTool,
                previewColor = session.activeColor.color,
            )
        }
    }
}

@Composable
private fun AnnotationOverlay(
    zoomableState: ZoomableState,
    annotations: List<Annotation>,
    previewPoints: List<Float>?,
    previewTool: AnnotationTool,
    previewColor: androidx.compose.ui.graphics.Color,
) {
    Canvas(Modifier.fillMaxSize()) {
        val bounds = zoomableState.transformedContentBounds
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

        fun toCanvas(x: Float, y: Float) =
            Offset(bounds.left + x * bounds.width, bounds.top + y * bounds.height)

        val strokeWidth = (bounds.width * 0.02f).coerceIn(8f, 96f)

        for (annotation in annotations) {
            val color = annotation.color.color
            when (annotation.type) {
                AnnotationType.RECT -> if (annotation.points.size == 4) {
                    val topLeft = toCanvas(annotation.points[0], annotation.points[1])
                    val bottomRight = toCanvas(annotation.points[2], annotation.points[3])
                    drawRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = topLeft,
                        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                    )
                }
                AnnotationType.FREEHAND -> drawFreehand(
                    annotation.points, color.copy(alpha = 0.45f), strokeWidth, ::toCanvas,
                )
                AnnotationType.TEXT_HIGHLIGHT -> Unit
            }
        }

        previewPoints?.let { points ->
            when (previewTool) {
                AnnotationTool.RECT -> if (points.size == 4) {
                    val topLeft = toCanvas(points[0], points[1])
                    val bottomRight = toCanvas(points[2], points[3])
                    drawRect(
                        color = previewColor.copy(alpha = 0.45f),
                        topLeft = topLeft,
                        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                    )
                }
                AnnotationTool.FREEHAND -> drawFreehand(
                    points, previewColor.copy(alpha = 0.55f), strokeWidth, ::toCanvas,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFreehand(
    points: List<Float>,
    color: androidx.compose.ui.graphics.Color,
    strokeWidth: Float,
    toCanvas: (Float, Float) -> Offset,
) {
    if (points.size < 4) return
    val path = Path()
    val first = toCanvas(points[0], points[1])
    path.moveTo(first.x, first.y)
    for (i in 2 until points.size step 2) {
        val point = toCanvas(points[i], points[i + 1])
        path.lineTo(point.x, point.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** Hit test in normalized coordinates. */
private fun hitTest(annotation: Annotation, x: Float, y: Float): Boolean = when (annotation.type) {
    AnnotationType.RECT -> annotation.points.size == 4 &&
        x >= annotation.points[0] - 0.01f && x <= annotation.points[2] + 0.01f &&
        y >= annotation.points[1] - 0.01f && y <= annotation.points[3] + 0.01f

    AnnotationType.FREEHAND -> {
        val points = annotation.points
        var hit = false
        var i = 0
        while (i + 3 < points.size) {
            if (distanceToSegment(x, y, points[i], points[i + 1], points[i + 2], points[i + 3]) < 0.025f) {
                hit = true
                break
            }
            i += 2
        }
        hit
    }

    AnnotationType.TEXT_HIGHLIGHT -> false
}

private fun distanceToSegment(
    px: Float, py: Float,
    x1: Float, y1: Float,
    x2: Float, y2: Float,
): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else {
        (((px - x1) * dx + (py - y1) * dy) / lengthSquared).coerceIn(0f, 1f)
    }
    return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}
