package ch.weissheimer.poly.ui.viewer.renderers

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ch.weissheimer.poly.annotation.AnnotationSession
import ch.weissheimer.poly.annotation.AnnotationType
import ch.weissheimer.poly.annotation.ReAnchor
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

/** Documents larger than this (rendered text) are viewable but not annotatable. */
private const val MAX_ANNOTATABLE_TEXT = 2_000_000

/**
 * JS → Kotlin bridge, exposed as `Poly`. Callbacks are marshalled to the
 * main thread (WebView invokes them on its JavaBridge thread).
 */
class PolyJsBridge(
    private val onContentTap: () -> Unit,
    private val onLoadMoreRows: () -> Unit = {},
    private val onDocumentText: (String) -> Unit = {},
    private val onHighlightCreated: (Int, Int) -> Unit = { _, _ -> },
    private val onHighlightTapped: (String) -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onTap() {
        mainHandler.post { onContentTap() }
    }

    @JavascriptInterface
    fun onLoadMoreRows() {
        mainHandler.post { onLoadMoreRows() }
    }

    @JavascriptInterface
    fun onDocumentText(text: String) {
        mainHandler.post { onDocumentText(text) }
    }

    @JavascriptInterface
    fun onHighlightCreated(start: Int, end: Int) {
        mainHandler.post { onHighlightCreated(start, end) }
    }

    @JavascriptInterface
    fun onHighlightTapped(id: String) {
        mainHandler.post { onHighlightTapped(id) }
    }
}

/**
 * WebView locked down for local content: JS on (bridge + highlighting),
 * every network request blocked. With a [session], the injected script wires
 * text highlights (CSS Custom Highlight API) into the annotation mode.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RestrictedWebView(
    html: String,
    session: AnnotationSession?,
    onContentTap: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMoreRows: () -> Unit = {},
    /** Scope for created/displayed highlights (xlsx: sheet index). */
    pageIndex: Int? = null,
    /** Receives a provider for export HTML with baked-in highlights. */
    onExportProvider: ((suspend () -> String?)?) -> Unit = {},
) {
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    val documentText = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(webViewHolder.value) {
        val webView = webViewHolder.value ?: return@LaunchedEffect
        onExportProvider {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                webView.evaluateJavascript(
                    "window.PolyHighlight ? PolyHighlight.exportHtml() : null"
                ) { result ->
                    val html = result
                        ?.takeIf { it != "null" }
                        ?.let { runCatching { org.json.JSONTokener(it).nextValue() as? String }.getOrNull() }
                    continuation.resume(html) { _, _, _ -> }
                }
            }
        }
    }

    val bridge = remember(session) {
        PolyJsBridge(
            onContentTap = onContentTap,
            onLoadMoreRows = onLoadMoreRows,
            onDocumentText = { text ->
                if (text.length <= MAX_ANNOTATABLE_TEXT) {
                    documentText.value = text
                    session?.load(text)
                }
            },
            onHighlightCreated = { start, end ->
                val text = documentText.value ?: return@PolyJsBridge
                if (session != null && end <= text.length && start < end) {
                    session.add(
                        session.newTextHighlight(ReAnchor.contextFor(text, start, end), pageIndex)
                    )
                }
            },
            onHighlightTapped = { id ->
                if (session != null) {
                    session.editTarget = session.annotations.firstOrNull { it.id == id }
                }
            },
        )
    }

    // Push mode + annotations into the page whenever either changes.
    if (session != null) {
        LaunchedEffect(session, webViewHolder.value) {
            val webView = webViewHolder.value ?: return@LaunchedEffect
            androidx.compose.runtime.snapshotFlow {
                Triple(
                    session.annotations
                        .filter { it.type == AnnotationType.TEXT_HIGHLIGHT && it.pageIndex == pageIndex }
                        .mapNotNull { annotation ->
                            annotation.anchor?.let {
                                Triple(annotation.id, it.startOffset to it.endOffset, annotation.color.name)
                            }
                        },
                    session.modeActive,
                    session.loaded,
                )
            }
                .distinctUntilChanged()
                .collect { (highlights, modeActive, _) ->
                    val json = JSONArray().apply {
                        highlights.forEach { (id, range, color) ->
                            put(
                                JSONObject()
                                    .put("id", id)
                                    .put("start", range.first)
                                    .put("end", range.second)
                                    .put("color", color)
                            )
                        }
                    }
                    val itemsLiteral = JSONObject.quote(json.toString())
                    webView.evaluateJavascript(
                        "if (window.PolyHighlight) { PolyHighlight.setItems($itemsLiteral); " +
                            "PolyHighlight.setMode($modeActive); }",
                        null,
                    )
                }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.setSupportZoom(true)
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                addJavascriptInterface(bridge, "Poly")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        // data: URIs never reach this; anything else is blocked.
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = true // no in-page navigation

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(HIGHLIGHT_SCRIPT, null)
                    }
                }
                webViewHolder.value = this
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
    )
}
