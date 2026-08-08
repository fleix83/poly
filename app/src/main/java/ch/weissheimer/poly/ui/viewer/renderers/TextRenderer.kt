package ch.weissheimer.poly.ui.viewer.renderers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.weissheimer.poly.R
import ch.weissheimer.poly.annotation.AnnotationType
import ch.weissheimer.poly.annotation.ReAnchor
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.TextTooLargeException
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.RendererError
import ch.weissheimer.poly.ui.viewer.RendererLoading
import ch.weissheimer.poly.ui.viewer.ViewerState
import ch.weissheimer.poly.ui.viewer.annotationGestures
import ch.weissheimer.poly.ui.viewer.pinchToZoom

private sealed interface TextLoad {
    data object Loading : TextLoad
    data class Loaded(val text: String) : TextLoad
    data object TooLarge : TextLoad
    data object Failed : TextLoad
}

/** Maps container positions to global character offsets for visible lines. */
private class TextLineRegistry(text: String) {
    val lines: List<String> = text.split('\n')

    /** Global start offset per line (separator = 1 char). */
    val lineStarts: IntArray = IntArray(lines.size).also { starts ->
        var offset = 0
        lines.forEachIndexed { index, line ->
            starts[index] = offset
            offset += line.length + 1
        }
    }

    var containerCoords: LayoutCoordinates? = null
    val lineCoords = HashMap<Int, LayoutCoordinates>()
    val lineLayouts = HashMap<Int, TextLayoutResult>()

    fun globalOffsetAt(position: Offset): Int? {
        val container = containerCoords ?: return null
        if (!container.isAttached) return null
        for ((index, coords) in lineCoords) {
            if (!coords.isAttached) continue
            val layout = lineLayouts[index] ?: continue
            val local = coords.localPositionOf(container, position)
            if (local.y < 0f || local.y > layout.size.height) continue
            val clamped = Offset(
                local.x.coerceIn(0f, layout.size.width.toFloat()),
                local.y,
            )
            val localOffset = layout.getOffsetForPosition(clamped)
            return lineStarts[index] + localOffset.coerceAtMost(lines[index].length)
        }
        return null
    }
}

class TextRenderer(private val fileRepository: FileRepository) : DocumentRenderer {

    override val capabilities = RendererCapabilities(textSelectable = true, zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        var reloadKey by remember { mutableIntStateOf(0) }
        val load by produceState<TextLoad>(TextLoad.Loading, state.document.uri, reloadKey) {
            value = TextLoad.Loading
            value = try {
                TextLoad.Loaded(fileRepository.openText(state.document.uri))
            } catch (e: TextTooLargeException) {
                TextLoad.TooLarge
            } catch (e: Exception) {
                TextLoad.Failed
            }
        }

        when (val l = load) {
            TextLoad.Loading -> RendererLoading(modifier)
            TextLoad.TooLarge -> RendererError(stringResource(R.string.viewer_text_too_large), modifier)
            TextLoad.Failed -> RendererError(
                stringResource(R.string.viewer_error_open), modifier,
                onRetry = { reloadKey++ },
            )
            is TextLoad.Loaded -> TextContent(l.text, state, modifier)
        }
    }

    @Composable
    private fun TextContent(text: String, state: ViewerState, modifier: Modifier) {
        val session = state.annotations
        LaunchedEffect(text) { session.load(text) }

        val registry = remember(text) { TextLineRegistry(text) }
        var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
        val fontSize = (15f * fontScale).coerceIn(9f, 44f).sp
        val fontFamily = if (state.monospace) FontFamily.Monospace else FontFamily.Default

        // Live selection preview while dragging (global offsets, end exclusive).
        var dragAnchor by remember { mutableStateOf<Int?>(null) }
        var preview by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned { registry.containerCoords = it }
                .pinchToZoom { zoom -> fontScale = (fontScale * zoom).coerceIn(0.6f, 3f) }
                .annotationGestures(
                    enabled = session.modeActive,
                    onTap = { position ->
                        val offset = registry.globalOffsetAt(position) ?: return@annotationGestures
                        session.editTarget = session.annotations.firstOrNull { annotation ->
                            val anchor = annotation.anchor
                            anchor != null && offset >= anchor.startOffset && offset < anchor.endOffset
                        }
                    },
                    onDragStart = { position ->
                        dragAnchor = registry.globalOffsetAt(position)
                        preview = null
                    },
                    onDrag = { position ->
                        val anchor = dragAnchor ?: return@annotationGestures
                        val current = registry.globalOffsetAt(position) ?: return@annotationGestures
                        preview = minOf(anchor, current) to maxOf(anchor, current)
                    },
                    onDragEnd = { cancelled ->
                        val range = preview
                        if (!cancelled && range != null && range.second > range.first) {
                            session.add(
                                session.newTextHighlight(
                                    ReAnchor.contextFor(text, range.first, range.second)
                                )
                            )
                        }
                        preview = null
                        dragAnchor = null
                    },
                )
                .pointerInput(Unit) { detectTapGestures(onTap = { state.onContentTap() }) },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 24.dp + state.topContentInset,
                    bottom = 24.dp,
                ),
            ) {
                itemsIndexed(registry.lines) { index, line ->
                    val lineStart = registry.lineStarts[index]
                    val lineEnd = lineStart + line.length
                    val annotated = buildAnnotatedString {
                        append(line.ifEmpty { " " })
                        for (annotation in session.annotations) {
                            if (annotation.type != AnnotationType.TEXT_HIGHLIGHT) continue
                            val anchor = annotation.anchor ?: continue
                            addLineSpan(
                                lineStart, lineEnd, line.length,
                                anchor.startOffset, anchor.endOffset,
                                SpanStyle(background = annotation.color.color.copy(alpha = 0.4f)),
                            )
                        }
                        preview?.let { (start, end) ->
                            addLineSpan(
                                lineStart, lineEnd, line.length, start, end,
                                SpanStyle(background = session.activeColor.color.copy(alpha = 0.55f)),
                            )
                        }
                    }
                    Text(
                        text = annotated,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        lineHeight = fontSize * 1.45f,
                        color = MaterialTheme.colorScheme.onSurface,
                        onTextLayout = { registry.lineLayouts[index] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { registry.lineCoords[index] = it },
                    )
                }
            }
        }
    }
}

/** Adds [style] for the intersection of a global range with this line. */
private fun AnnotatedString.Builder.addLineSpan(
    lineStart: Int,
    lineEnd: Int,
    lineLength: Int,
    globalStart: Int,
    globalEnd: Int,
    style: SpanStyle,
) {
    val start = (globalStart - lineStart).coerceIn(0, lineLength)
    val end = (globalEnd - lineStart).coerceIn(0, lineLength)
    if (end > start) addStyle(style, start, end)
}
