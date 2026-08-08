package ch.weissheimer.poly.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.weissheimer.poly.R
import ch.weissheimer.poly.annotation.AnnotationSession
import ch.weissheimer.poly.data.DocumentInfo

/** Shared state and callbacks handed to every renderer. */
class ViewerState(
    val document: DocumentInfo,
    val annotations: AnnotationSession,
    /** Single tap on content: toggle the viewer chrome. */
    val onContentTap: () -> Unit,
) {
    /** Toolbar-controlled option for the text renderer. */
    var monospace by mutableStateOf(false)
}

data class RendererCapabilities(
    val textSelectable: Boolean = false,
    val pageable: Boolean = false,
    val zoomable: Boolean = false,
)

/** One renderer per format group, all presented inside the same viewer frame. */
interface DocumentRenderer {
    val capabilities: RendererCapabilities

    @Composable
    fun Content(state: ViewerState, modifier: Modifier)
}

/** Placeholder for formats whose renderer arrives in a later phase. */
class UnsupportedRenderer : DocumentRenderer {
    override val capabilities = RendererCapabilities()

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Default.UnfoldMore,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    stringResource(
                        R.string.viewer_unsupported,
                        stringResource(state.document.format.labelRes),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun RendererLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun RendererError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(message, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Button(onClick = onRetry) { Text(stringResource(R.string.viewer_retry)) }
            }
        }
    }
}
