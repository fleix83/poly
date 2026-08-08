package ch.weissheimer.poly.ui.viewer.renderers

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.weissheimer.poly.R
import ch.weissheimer.poly.annotation.AnnotationType
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.RendererError
import ch.weissheimer.poly.ui.viewer.RendererLoading
import ch.weissheimer.poly.ui.viewer.ViewerState
import ch.weissheimer.poly.ui.viewer.annotationGestures
import ch.weissheimer.poly.ui.viewer.pinchToZoom
import ch.weissheimer.poly.viewer.pdf.PdfPageStore
import ch.weissheimer.poly.viewer.pdf.PdfPasswordRequiredException
import ch.weissheimer.poly.viewer.pdf.PdfWrongPasswordException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class NeedsPassword(val wrongAttempt: Boolean) : PdfLoad
    data class Ready(val store: PdfPageStore) : PdfLoad
    data object Failed : PdfLoad
}

class PdfViewRenderer : DocumentRenderer {

    override val capabilities = RendererCapabilities(pageable = true, zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        val context = LocalContext.current.applicationContext
        val store = remember(state.document) {
            PdfPageStore(context, state.document.uri, state.document.sha256)
        }
        var password by remember { mutableStateOf<String?>(null) }
        var attempt by remember { mutableIntStateOf(0) }

        val load by produceState<PdfLoad>(PdfLoad.Loading, store, attempt) {
            value = PdfLoad.Loading
            value = try {
                store.open(password)
                PdfLoad.Ready(store)
            } catch (e: PdfPasswordRequiredException) {
                PdfLoad.NeedsPassword(wrongAttempt = false)
            } catch (e: PdfWrongPasswordException) {
                PdfLoad.NeedsPassword(wrongAttempt = true)
            } catch (e: Exception) {
                PdfLoad.Failed
            }
        }

        androidx.compose.runtime.DisposableEffect(store) {
            onDispose { store.close() }
        }

        when (val l = load) {
            PdfLoad.Loading -> RendererLoading(modifier)
            PdfLoad.Failed -> RendererError(stringResource(R.string.pdf_error), modifier)
            is PdfLoad.NeedsPassword -> {
                RendererError(stringResource(R.string.pdf_password_title), modifier)
                PasswordDialog(
                    wrongAttempt = l.wrongAttempt,
                    onConfirm = { entered ->
                        password = entered
                        attempt++
                    },
                )
            }
            is PdfLoad.Ready -> PdfPages(l.store, state, modifier)
        }
    }
}

@Composable
private fun PasswordDialog(
    wrongAttempt: Boolean,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.pdf_password_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.pdf_password_prompt))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = wrongAttempt,
                    supportingText = if (wrongAttempt) {
                        { Text(stringResource(R.string.pdf_password_wrong)) }
                    } else null,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotEmpty()) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}

/** Container-space rect preview while dragging on a page. */
private class PdfDragPreview(
    val pageIndex: Int,
    val rect: List<Float>,
)

@Composable
private fun PdfPages(store: PdfPageStore, state: ViewerState, modifier: Modifier) {
    val session = state.annotations
    LaunchedEffect(Unit) { session.load(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidth = maxWidth
        val baseWidthPx = with(density) { viewportWidth.roundToPx() }

        var zoom by remember { mutableFloatStateOf(1f) }
        var settledZoom by remember { mutableFloatStateOf(1f) }
        val listState = rememberLazyListState()
        val hScroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        var sliderVisible by remember { mutableStateOf(false) }

        val currentPage by remember {
            derivedStateOf { listState.firstVisibleItemIndex + 1 }
        }
        val renderWidthPx = (baseWidthPx * settledZoom).roundToInt()

        var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val pageCoords = remember { HashMap<Int, LayoutCoordinates>() }
        var dragPage by remember { mutableStateOf<Int?>(null) }
        var dragStartNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        var preview by remember { mutableStateOf<PdfDragPreview?>(null) }

        /** Container position → (pageIndex, normalized page coords). */
        fun locate(position: Offset): Triple<Int, Float, Float>? {
            val container = containerCoords ?: return null
            if (!container.isAttached) return null
            for ((index, coords) in pageCoords) {
                if (!coords.isAttached) continue
                val local = coords.localPositionOf(container, position)
                val size = coords.size
                if (local.y >= 0f && local.y <= size.height) {
                    return Triple(
                        index,
                        (local.x / size.width).coerceIn(0f, 1f),
                        (local.y / size.height).coerceIn(0f, 1f),
                    )
                }
            }
            return null
        }

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerCoords = it }
                .annotationGestures(
                    enabled = session.modeActive,
                    onTap = { position ->
                        val (page, x, y) = locate(position) ?: return@annotationGestures
                        session.editTarget = session.annotations.lastOrNull { annotation ->
                            annotation.pageIndex == page &&
                                annotation.type == AnnotationType.RECT &&
                                annotation.points.size == 4 &&
                                x >= annotation.points[0] - 0.01f && x <= annotation.points[2] + 0.01f &&
                                y >= annotation.points[1] - 0.01f && y <= annotation.points[3] + 0.01f
                        }
                    },
                    onDragStart = { position ->
                        val located = locate(position)
                        dragPage = located?.first
                        dragStartNorm = located?.let { it.second to it.third }
                        preview = null
                    },
                    onDrag = { position ->
                        val page = dragPage ?: return@annotationGestures
                        val start = dragStartNorm ?: return@annotationGestures
                        val located = locate(position) ?: return@annotationGestures
                        // Rect stays on the page where the drag started.
                        val (x, y) = if (located.first == page) {
                            located.second to located.third
                        } else return@annotationGestures
                        preview = PdfDragPreview(
                            pageIndex = page,
                            rect = listOf(
                                minOf(start.first, x), minOf(start.second, y),
                                maxOf(start.first, x), maxOf(start.second, y),
                            ),
                        )
                    },
                    onDragEnd = { cancelled ->
                        val current = preview
                        if (!cancelled && current != null &&
                            current.rect[2] - current.rect[0] > 0.01f &&
                            current.rect[3] - current.rect[1] > 0.01f
                        ) {
                            session.add(
                                session.newShape(
                                    AnnotationType.RECT, current.rect, current.pageIndex,
                                )
                            )
                        }
                        preview = null
                        dragPage = null
                        dragStartNorm = null
                    },
                )
                .pinchToZoom(
                    onZoomEnd = { settledZoom = zoom },
                ) { change -> zoom = (zoom * change).coerceIn(1f, 4f) }
                .pointerInput(Unit) { detectTapGestures(onTap = { state.onContentTap() }) },
        ) {
            Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .width(viewportWidth * zoom)
                        .fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = store.pageCount,
                        key = { it },
                    ) { index ->
                        PdfPage(
                            store = store,
                            index = index,
                            renderWidthPx = renderWidthPx,
                            aspect = store.pageAspects.getOrElse(index) { 1.414f },
                            session = session,
                            preview = preview,
                            onPositioned = { coords -> pageCoords[index] = coords },
                        )
                    }
                }
            }

            // "3/12" indicator, tap opens the page slider.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                onClick = { sliderVisible = true },
            ) {
                Text(
                    stringResource(R.string.pdf_page_indicator, currentPage, store.pageCount),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (sliderVisible) {
            var target by remember { mutableFloatStateOf(currentPage.toFloat()) }
            AlertDialog(
                onDismissRequest = { sliderVisible = false },
                title = { Text(stringResource(R.string.pdf_goto_page)) },
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            stringResource(
                                R.string.pdf_page_indicator,
                                target.roundToInt(),
                                store.pageCount,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (store.pageCount > 1) {
                            Slider(
                                value = target,
                                onValueChange = { target = it },
                                valueRange = 1f..store.pageCount.toFloat(),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        sliderVisible = false
                        scope.launch { listState.scrollToItem(target.roundToInt() - 1) }
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { sliderVisible = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun PdfPage(
    store: PdfPageStore,
    index: Int,
    renderWidthPx: Int,
    aspect: Float,
    session: ch.weissheimer.poly.annotation.AnnotationSession,
    preview: PdfDragPreview?,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, store, index, renderWidthPx) {
        value = runCatching { store.renderPage(index, renderWidthPx) }.getOrNull() ?: value
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspect)
            .background(androidx.compose.ui.graphics.Color.White)
            .onGloballyPositioned(onPositioned),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            fun drawNormalizedRect(points: List<Float>, color: androidx.compose.ui.graphics.Color) {
                if (points.size != 4) return
                drawRect(
                    color = color,
                    topLeft = Offset(points[0] * size.width, points[1] * size.height),
                    size = androidx.compose.ui.geometry.Size(
                        (points[2] - points[0]) * size.width,
                        (points[3] - points[1]) * size.height,
                    ),
                )
            }
            for (annotation in session.annotations) {
                if (annotation.pageIndex == index && annotation.type == AnnotationType.RECT) {
                    drawNormalizedRect(annotation.points, annotation.color.color.copy(alpha = 0.35f))
                }
            }
            if (preview?.pageIndex == index) {
                drawNormalizedRect(preview.rect, session.activeColor.color.copy(alpha = 0.45f))
            }
        }
    }
}
