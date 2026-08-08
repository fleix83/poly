package ch.weissheimer.poly.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First-page thumbnails for PDFs in the recents list, cached on disk by
 * content hash. Images use Coil directly; other formats keep their icon.
 */
class ThumbnailStore(private val context: Context) {

    suspend fun pdfThumbnail(uri: Uri, hash: String): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val target = File(dir, "$hash.png")
        if (target.length() > 0) return@withContext target
        runCatching {
            val source = LocalFileCache.localCopy(context, uri, hash, "pdf")
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val width = 192
                        val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val tmp = File(dir, "$hash.tmp")
                        tmp.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                        }
                        bitmap.recycle()
                        tmp.renameTo(target)
                    }
                }
            }
            target.takeIf { it.length() > 0 }
        }.getOrNull()
    }
}
