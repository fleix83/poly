package ch.weissheimer.poly.viewer.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PdfPasswordRequiredException : Exception()
class PdfWrongPasswordException : Exception()

/**
 * Owns the framework [PdfRenderer] for one document: local cache copy,
 * optional pdfbox decryption for password-protected files, serialized page
 * access (PdfRenderer is single-threaded) and an LRU bitmap cache sized to a
 * quarter of the app's max heap.
 */
class PdfPageStore(
    private val context: Context,
    private val uri: Uri,
    private val fileHash: String,
) {
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()

    var pageCount: Int = 0
        private set

    /** height/width per page, for stable list item sizing. */
    var pageAspects: List<Float> = emptyList()
        private set

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /**
     * @throws PdfPasswordRequiredException file is encrypted, no password given
     * @throws PdfWrongPasswordException password did not match
     */
    suspend fun open(password: String?) = withContext(Dispatchers.IO) {
        val local = localCopy()
        val source = if (password.isNullOrEmpty()) local else decrypt(local, password)
        try {
            val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            val opened = PdfRenderer(descriptor)
            fd = descriptor
            renderer = opened
            pageCount = opened.pageCount
            pageAspects = (0 until pageCount).map { index ->
                opened.openPage(index).use { page -> page.height.toFloat() / page.width }
            }
        } catch (e: SecurityException) {
            throw PdfPasswordRequiredException()
        }
    }

    suspend fun renderPage(index: Int, widthPx: Int): Bitmap {
        val clampedWidth = widthPx.coerceIn(64, MAX_BITMAP_WIDTH)
        val key = "$index@$clampedWidth"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            cache.get(key)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val opened = checkNotNull(renderer) { "PdfPageStore not opened" }
                opened.openPage(index).use { page ->
                    val heightPx = (clampedWidth.toLong() * page.height / page.width).toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(clampedWidth, heightPx, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bitmap)
                    bitmap
                }
            }
        }
    }

    fun close() {
        runCatching { renderer?.close() }
        runCatching { fd?.close() }
        renderer = null
        fd = null
        cache.evictAll()
    }

    /** PdfRenderer needs a seekable file; content streams are copied once per hash. */
    private fun localCopy(): File {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val target = File(dir, "$fileHash.pdf")
        if (target.length() > 0) return target
        context.contentResolver.openInputStream(uri)?.use { input ->
            val tmp = File(dir, "$fileHash.tmp")
            tmp.outputStream().use { output -> input.copyTo(output) }
            tmp.renameTo(target)
        } ?: throw FileNotFoundException("No stream for $uri")
        return target
    }

    private fun decrypt(encrypted: File, password: String): File {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        val target = File(encrypted.parentFile, "$fileHash-dec.pdf")
        if (target.length() > 0) return target
        try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(encrypted, password).use { document ->
                document.isAllSecurityToBeRemoved = true
                document.save(target)
            }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PdfWrongPasswordException()
        } catch (e: IOException) {
            throw PdfWrongPasswordException()
        }
        return target
    }

    private companion object {
        const val MAX_BITMAP_WIDTH = 4096

        fun cacheSizeKb(): Int {
            val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return maxKb / 4
        }
    }
}
