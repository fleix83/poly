package ch.weissheimer.poly.ui.viewer.renderers

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.weissheimer.poly.R
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.TextTooLargeException
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.RendererError
import ch.weissheimer.poly.ui.viewer.RendererLoading
import ch.weissheimer.poly.ui.viewer.ViewerState
import ch.weissheimer.poly.ui.viewer.pinchToZoom

private sealed interface TextLoad {
    data object Loading : TextLoad
    data class Loaded(val text: String) : TextLoad
    data object TooLarge : TextLoad
    data object Failed : TextLoad
}

class TextRenderer(private val fileRepository: FileRepository) : DocumentRenderer {

    override val capabilities = RendererCapabilities(textSelectable = true, zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        var reloadKey by remember { androidx.compose.runtime.mutableIntStateOf(0) }
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
        val lines = remember(text) { text.lines() }
        var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
        val fontSize = (15f * fontScale).coerceIn(9f, 44f).sp
        val fontFamily = if (state.monospace) FontFamily.Monospace else FontFamily.Default

        Box(
            modifier
                .fillMaxSize()
                .pinchToZoom { zoom -> fontScale = (fontScale * zoom).coerceIn(0.6f, 3f) }
                .pointerInput(Unit) { detectTapGestures(onTap = { state.onContentTap() }) },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp, vertical = 24.dp,
                ),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = line.ifEmpty { " " },
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        lineHeight = fontSize * 1.45f,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
