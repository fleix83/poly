package ch.weissheimer.poly.ui.viewer.renderers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.weissheimer.poly.R
import ch.weissheimer.poly.data.FileRepository
import ch.weissheimer.poly.data.TextTooLargeException
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.RendererError
import ch.weissheimer.poly.ui.viewer.RendererLoading
import ch.weissheimer.poly.ui.viewer.ViewerState

private sealed interface HtmlLoad {
    data object Loading : HtmlLoad
    data class Ready(val html: String, val hasExternalContent: Boolean) : HtmlLoad
    data object TooLarge : HtmlLoad
    data object Failed : HtmlLoad
}

private val EXTERNAL_REF = Regex("""(?i)(src|href)\s*=\s*["']https?://""")

/** .html files in the same restricted WebView; external resources stay blocked. */
class HtmlRenderer(private val fileRepository: FileRepository) : DocumentRenderer {

    override val capabilities = RendererCapabilities(textSelectable = true, zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        var reloadKey by remember { mutableIntStateOf(0) }
        val load by produceState<HtmlLoad>(HtmlLoad.Loading, state.document.uri, reloadKey) {
            value = HtmlLoad.Loading
            value = try {
                val html = fileRepository.openText(state.document.uri)
                HtmlLoad.Ready(html, hasExternalContent = EXTERNAL_REF.containsMatchIn(html))
            } catch (e: TextTooLargeException) {
                HtmlLoad.TooLarge
            } catch (e: Exception) {
                HtmlLoad.Failed
            }
        }

        when (val l = load) {
            HtmlLoad.Loading -> RendererLoading(modifier)
            HtmlLoad.TooLarge -> RendererError(stringResource(R.string.viewer_text_too_large), modifier)
            HtmlLoad.Failed -> RendererError(
                stringResource(R.string.viewer_error_open), modifier,
                onRetry = { reloadKey++ },
            )
            is HtmlLoad.Ready -> Column(modifier.fillMaxSize()) {
                if (l.hasExternalContent) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(R.string.html_external_blocked),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                RestrictedWebView(
                    html = l.html,
                    session = state.annotations,
                    onContentTap = { state.onContentTap() },
                    onExportProvider = { state.exportHtmlProvider = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
