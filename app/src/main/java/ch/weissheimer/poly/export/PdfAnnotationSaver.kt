package ch.weissheimer.poly.export

import android.content.Context
import ch.weissheimer.poly.annotation.Annotation
import ch.weissheimer.poly.annotation.AnnotationType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import java.io.File
import java.io.OutputStream

/**
 * Writes Poly's area markings into a copy of the PDF as real Square
 * annotations (the rect fallback of the engine decision), visible in
 * Adobe Reader/Drive.
 */
object PdfAnnotationSaver {

    fun save(
        context: Context,
        source: File,
        annotations: List<Annotation>,
        output: OutputStream,
    ) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(source).use { document ->
            for (annotation in annotations) {
                if (annotation.type != AnnotationType.RECT) continue
                val pageIndex = annotation.pageIndex ?: continue
                if (pageIndex !in 0 until document.numberOfPages) continue
                if (annotation.points.size != 4) continue

                val page = document.getPage(pageIndex)
                val box = page.mediaBox
                val x0 = box.lowerLeftX + annotation.points[0] * box.width
                val x1 = box.lowerLeftX + annotation.points[2] * box.width
                // Normalized y grows downwards, PDF y grows upwards.
                val yTop = box.upperRightY - annotation.points[1] * box.height
                val yBottom = box.upperRightY - annotation.points[3] * box.height

                val composeColor = annotation.color.color
                val rgb = floatArrayOf(composeColor.red, composeColor.green, composeColor.blue)

                val square = PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE)
                square.rectangle = PDRectangle(x0, yBottom, x1 - x0, yTop - yBottom)
                square.color = PDColor(rgb, PDDeviceRGB.INSTANCE)
                square.interiorColor = PDColor(rgb, PDDeviceRGB.INSTANCE)
                square.constantOpacity = 0.35f
                square.contents = "Poly Markierung"
                page.annotations.add(square)
            }
            document.save(output)
        }
    }
}
