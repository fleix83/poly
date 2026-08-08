package ch.weissheimer.poly.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException

/**
 * Content URIs are streams; some consumers (PdfRenderer, ZipFile) need a
 * seekable file. One cached copy per content hash.
 */
object LocalFileCache {

    fun localCopy(context: Context, uri: Uri, hash: String, extension: String): File {
        val dir = File(context.cacheDir, "docs").apply { mkdirs() }
        val target = File(dir, "$hash.$extension")
        if (target.length() > 0) return target
        context.contentResolver.openInputStream(uri)?.use { input ->
            val tmp = File(dir, "$hash.$extension.tmp")
            tmp.outputStream().use { output -> input.copyTo(output) }
            tmp.renameTo(target)
        } ?: throw FileNotFoundException("No stream for $uri")
        return target
    }

    fun cacheFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "html").apply { mkdirs() }
        return File(dir, name)
    }
}
