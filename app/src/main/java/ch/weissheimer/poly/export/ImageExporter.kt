package ch.weissheimer.poly.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import ch.weissheimer.poly.annotation.Annotation
import ch.weissheimer.poly.annotation.AnnotationType
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Burns rect/freehand markings into a copy of the image (flattening).
 * Animated GIFs are exported as a still of the first frame.
 */
object ImageExporter {

    suspend fun flatten(
        context: Context,
        uri: Uri,
        annotations: List<Annotation>,
        output: OutputStream,
        asPng: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        val source = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("Cannot decode $uri")

        val bitmap = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true).also {
                if (it != source) source.recycle()
            }
        }

        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val strokeWidth = (width * 0.02f).coerceAtLeast(6f)

        for (annotation in annotations) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(
                    (0.4f * 255).toInt(),
                    (annotation.color.color.red * 255).toInt(),
                    (annotation.color.color.green * 255).toInt(),
                    (annotation.color.color.blue * 255).toInt(),
                )
            }
            when (annotation.type) {
                AnnotationType.RECT -> if (annotation.points.size == 4) {
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(
                        annotation.points[0] * width,
                        annotation.points[1] * height,
                        annotation.points[2] * width,
                        annotation.points[3] * height,
                        paint,
                    )
                }
                AnnotationType.FREEHAND -> if (annotation.points.size >= 4) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = strokeWidth
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    val path = Path()
                    path.moveTo(annotation.points[0] * width, annotation.points[1] * height)
                    var i = 2
                    while (i + 1 < annotation.points.size) {
                        path.lineTo(annotation.points[i] * width, annotation.points[i + 1] * height)
                        i += 2
                    }
                    canvas.drawPath(path, paint)
                }
                AnnotationType.TEXT_HIGHLIGHT -> Unit
            }
        }

        val format = if (asPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bitmap.compress(format, 92, output)
        bitmap.recycle()
    }
}
