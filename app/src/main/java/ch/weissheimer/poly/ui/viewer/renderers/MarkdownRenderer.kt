package ch.weissheimer.poly.ui.viewer.renderers

import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ch.weissheimer.poly.R
import ch.weissheimer.poly.annotation.AnnotationSession
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

/** Marker span type so our highlights can be removed without touching Markwon spans. */
private class PolyHighlightSpan(color: Int) : BackgroundColorSpan(color)

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
        val session = state.annotations
        var fontScale by rememberSaveable { mutableFloatStateOf(1f) }
        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        val linkColor = MaterialTheme.colorScheme.primary.toArgb()

        var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var textViewRef by remember { mutableStateOf<TextView?>(null) }
        var textViewCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

        // Live selection preview (offsets into the rendered text, end exclusive).
        var dragAnchor by remember { mutableStateOf<Int?>(null) }
        var preview by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        fun offsetAt(position: Offset): Int? {
            val container = containerCoords ?: return null
            val coords = textViewCoords ?: return null
            val textView = textViewRef ?: return null
            if (!container.isAttached || !coords.isAttached) return null
            if (textView.layout == null) return null
            val local = coords.localPositionOf(container, position)
            return textView.getOffsetForPosition(local.x, local.y)
                .takeIf { it >= 0 }
        }

        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned { containerCoords = it }
                .pinchToZoom { zoom -> fontScale = (fontScale * zoom).coerceIn(0.6f, 3f) }
                .annotationGestures(
                    enabled = session.modeActive,
                    onTap = { position ->
                        val offset = offsetAt(position) ?: return@annotationGestures
                        session.editTarget = session.annotations.firstOrNull { annotation ->
                            val anchor = annotation.anchor
                            anchor != null && offset >= anchor.startOffset && offset < anchor.endOffset
                        }
                    },
                    onDragStart = { position ->
                        dragAnchor = offsetAt(position)
                        preview = null
                    },
                    onDrag = { position ->
                        val anchor = dragAnchor ?: return@annotationGestures
                        val current = offsetAt(position) ?: return@annotationGestures
                        preview = minOf(anchor, current) to maxOf(anchor, current)
                    },
                    onDragEnd = { cancelled ->
                        val range = preview
                        val rendered = textViewRef?.text?.toString()
                        if (!cancelled && range != null && rendered != null &&
                            range.second > range.first && range.second <= rendered.length
                        ) {
                            session.add(
                                session.newTextHighlight(
                                    ReAnchor.contextFor(rendered, range.first, range.second)
                                )
                            )
                        }
                        preview = null
                        dragAnchor = null
                    },
                )
                .pointerInput(Unit) { detectTapGestures(onTap = { state.onContentTap() }) },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { textViewCoords = it },
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
                        textViewRef = textView
                        val markwon = textView.tag as Markwon
                        textView.setTextColor(textColor)
                        textView.setLinkTextColor(linkColor)
                        textView.textSize = 16f * fontScale
                        if (textView.getTag(R.id.poly_markdown_source) != markdown) {
                            textView.setTag(R.id.poly_markdown_source, markdown)
                            markwon.setMarkdown(textView, markdown)
                            session.load(textView.text.toString())
                        }
                        applyHighlights(textView, session, preview)
                    },
                )
            }
        }
    }

    private fun applyHighlights(
        textView: TextView,
        session: AnnotationSession,
        preview: Pair<Int, Int>?,
    ) {
        val text = textView.text
        val spannable = text as? Spannable ?: SpannableString(text).also {
            textView.setText(it, TextView.BufferType.SPANNABLE)
        }
        val target = textView.text as? Spannable ?: spannable
        target.getSpans(0, target.length, PolyHighlightSpan::class.java)
            .forEach { target.removeSpan(it) }
        for (annotation in session.annotations) {
            if (annotation.type != AnnotationType.TEXT_HIGHLIGHT) continue
            val anchor = annotation.anchor ?: continue
            if (anchor.endOffset > target.length || anchor.startOffset >= anchor.endOffset) continue
            target.setSpan(
                PolyHighlightSpan(annotation.color.color.copy(alpha = 0.4f).toArgb()),
                anchor.startOffset,
                anchor.endOffset,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        preview?.let { (start, end) ->
            if (end <= target.length && start < end) {
                target.setSpan(
                    PolyHighlightSpan(session.activeColor.color.copy(alpha = 0.55f).toArgb()),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        textView.invalidate()
    }
}
