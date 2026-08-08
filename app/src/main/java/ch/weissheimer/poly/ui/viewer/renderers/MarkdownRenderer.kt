package ch.weissheimer.poly.ui.viewer.renderers

import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ch.weissheimer.poly.R
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.TextTooLargeException
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.RendererError
import ch.weissheimer.poly.ui.viewer.RendererLoading
import ch.weissheimer.poly.ui.viewer.ViewerState
import ch.weissheimer.poly.ui.viewer.pinchToZoom
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

private sealed interface MarkdownLoad {
    data object Loading : MarkdownLoad
    data class Loaded(val markdown: String) : MarkdownLoad
    data object TooLarge : MarkdownLoad
    data object Failed : MarkdownLoad
}

class MarkdownRenderer(private val fileRepository: FileRepository) : DocumentRenderer {

    override val capabilities = RendererCapabilities(textSelectable = true, zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        var reloadKey by remember { mutableIntStateOf(0) }
        val load by produceState<MarkdownLoad>(MarkdownLoad.Loading, state.document.uri, reloadKey) {
            value = MarkdownLoad.Loading
            value = try {
                MarkdownLoad.Loaded(fileRepository.openText(state.document.uri))
            } catch (e: TextTooLargeException) {
                MarkdownLoad.TooLarge
            } catch (e: Exception) {
                MarkdownLoad.Failed
            }
        }

        when (val l = load) {
            MarkdownLoad.Loading -> RendererLoading(modifier)
            MarkdownLoad.TooLarge -> RendererError(stringResource(R.string.viewer_text_too_large), modifier)
            MarkdownLoad.Failed -> RendererError(
                stringResource(R.string.viewer_error_open), modifier,
                onRetry = { reloadKey++ },
            )
            is MarkdownLoad.Loaded -> MarkdownContent(l.markdown, state, modifier)
        }
    }

    @Composable
    private fun MarkdownContent(markdown: String, state: ViewerState, modifier: Modifier) {
        var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        val linkColor = MaterialTheme.colorScheme.primary.toArgb()

        Box(
            modifier
                .fillMaxSize()
                .pinchToZoom { zoom -> fontScale = (fontScale * zoom).coerceIn(0.6f, 3f) }
                .pointerInput(Unit) { detectTapGestures(onTap = { state.onContentTap() }) },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { context ->
                        val markwon = Markwon.builder(context)
                            .usePlugin(TablePlugin.create(context))
                            .usePlugin(StrikethroughPlugin.create())
                            .usePlugin(LinkifyPlugin.create())
                            .usePlugin(object : AbstractMarkwonPlugin() {
                                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                                    // Open links through the system chooser.
                                    builder.linkResolver { view, link ->
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                            view.context.startActivity(Intent.createChooser(intent, null))
                                        }
                                    }
                                }
                            })
                            .build()
                        TextView(context).apply {
                            tag = markwon
                            setOnClickListener { state.onContentTap() }
                        }
                    },
                    update = { textView ->
                        val markwon = textView.tag as Markwon
                        textView.setTextColor(textColor)
                        textView.setLinkTextColor(linkColor)
                        textView.textSize = 16f * fontScale
                        markwon.setMarkdown(textView, markdown)
                    },
                )
            }
        }
    }
}
