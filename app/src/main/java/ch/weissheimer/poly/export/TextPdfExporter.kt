package ch.weissheimer.poly.export

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/**
 * Paginates styled text (highlight backgrounds included as spans) onto A4
 * pages of an [android.graphics.pdf.PdfDocument]. Page breaks land between
 * lines, never through them.
 */
object TextPdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48

    fun export(text: CharSequence, output: OutputStream, monospace: Boolean = false) {
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.BLACK
            if (monospace) typeface = android.graphics.Typeface.MONOSPACE
        }
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val contentHeight = PAGE_HEIGHT - 2 * MARGIN
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .setIncludePad(false)
            .build()

        val pdf = PdfDocument()
        var pageNumber = 0
        var topLine = 0
        while (topLine < layout.lineCount) {
            val topY = layout.getLineTop(topLine)
            // Last line whose bottom still fits on this page.
            var bottomLine = topLine
            while (bottomLine + 1 < layout.lineCount &&
                layout.getLineBottom(bottomLine + 1) - topY <= contentHeight
            ) {
                bottomLine++
            }
            val sliceBottom = layout.getLineBottom(bottomLine)

            pageNumber++
            val page = pdf.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            val canvas = page.canvas
            canvas.translate(MARGIN.toFloat(), MARGIN.toFloat())
            canvas.clipRect(0, 0, contentWidth, sliceBottom - topY)
            canvas.translate(0f, -topY.toFloat())
            layout.draw(canvas)
            pdf.finishPage(page)

            topLine = bottomLine + 1
        }
        if (pageNumber == 0) {
            pdf.finishPage(
                pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            )
        }
        pdf.writeTo(output)
        pdf.close()
    }
}
