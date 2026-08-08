package ch.weissheimer.poly.ui.viewer.renderers

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ch.weissheimer.poly.R
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.RendererError
import ch.weissheimer.poly.ui.viewer.RendererLoading
import ch.weissheimer.poly.ui.viewer.ViewerState
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

@Composable
private fun PdfPages(store: PdfPageStore, state: ViewerState, modifier: Modifier) {
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

        Box(
            Modifier
                .fillMaxSize()
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
) {
    val bitmap by produceState<Bitmap?>(null, store, index, renderWidthPx) {
        value = runCatching { store.renderPage(index, renderWidthPx) }.getOrNull() ?: value
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspect)
            .background(androidx.compose.ui.graphics.Color.White),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
