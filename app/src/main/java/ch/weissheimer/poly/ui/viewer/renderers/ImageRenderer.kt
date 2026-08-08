package ch.weissheimer.poly.ui.viewer.renderers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ch.weissheimer.poly.ui.viewer.DocumentRenderer
import ch.weissheimer.poly.ui.viewer.RendererCapabilities
import ch.weissheimer.poly.ui.viewer.ViewerState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

class ImageRenderer : DocumentRenderer {

    override val capabilities = RendererCapabilities(zoomable = true)

    @Composable
    override fun Content(state: ViewerState, modifier: Modifier) {
        ZoomableAsyncImage(
            model = state.document.uri,
            contentDescription = state.document.displayName,
            modifier = modifier.fillMaxSize(),
            onClick = { state.onContentTap() },
        )
    }
}
