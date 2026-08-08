package ch.weissheimer.poly.export

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Renders HTML (with highlights already baked in as inline spans) to a PDF
 * via an offscreen WebView drawn slice-by-slice onto PdfDocument pages.
 */
object HtmlPdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun export(context: Context, html: String, output: OutputStream) {
        withContext(Dispatchers.Main) {
            WebView.enableSlowWholeDocumentDraw()
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = false
            webView.settings.blockNetworkLoads = true
            webView.settings.blockNetworkImage = true
            webView.settings.allowFileAccess = false
            webView.settings.allowContentAccess = false

            suspendCancellableCoroutine { continuation ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
            // Let layout and images settle.
            delay(400)

            webView.measure(
                View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val contentHeight = webView.measuredHeight.coerceAtLeast(1)
            webView.layout(0, 0, PAGE_WIDTH, contentHeight)

            val pdf = PdfDocument()
            var pageNumber = 0
            var top = 0
            while (top < contentHeight) {
                pageNumber++
                val page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                val canvas = page.canvas
                canvas.translate(0f, -top.toFloat())
                webView.draw(canvas)
                pdf.finishPage(page)
                top += PAGE_HEIGHT
            }
            withContext(Dispatchers.IO) {
                pdf.writeTo(output)
            }
            pdf.close()
            webView.destroy()
        }
    }
}
