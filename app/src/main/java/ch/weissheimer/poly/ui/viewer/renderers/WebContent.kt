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
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * JS → Kotlin bridge, exposed as `Poly`. Callbacks are marshalled to the
 * main thread (WebView invokes them on its JavaBridge thread).
 */
class PolyJsBridge(
    private val onContentTap: () -> Unit,
    private val onLoadMoreRows: () -> Unit = {},
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
}

/** Tap anywhere except links/buttons toggles the viewer chrome. */
private const val TAP_SCRIPT = """
document.addEventListener('click', function(e) {
  if (!e.target.closest('a') && !e.target.closest('button')) { Poly.onTap(); }
});
"""

/**
 * WebView locked down for local content: JS on (needed for the bridge and
 * later for highlighting), every network request blocked.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RestrictedWebView(
    html: String,
    bridge: PolyJsBridge,
    modifier: Modifier = Modifier,
) {
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
                        view.evaluateJavascript(TAP_SCRIPT, null)
                    }
                }
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
