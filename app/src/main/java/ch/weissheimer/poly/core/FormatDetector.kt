package ch.weissheimer.poly.core

/**
 * Detects the document format from MIME type, file name extension and magic
 * bytes. Magic bytes settle binary formats (renamed files, generic MIME),
 * then MIME type wins over the extension — except for text/plain, which file
 * managers report for anything text-like, where a more specific extension wins.
 */
object FormatDetector {

    private enum class Magic { PDF, PNG, JPEG, GIF, ZIP, HTML }

    private val mimeMap = mapOf(
        "text/plain" to DocumentFormat.TXT,
        "text/markdown" to DocumentFormat.MARKDOWN,
        "text/x-markdown" to DocumentFormat.MARKDOWN,
        "text/html" to DocumentFormat.HTML,
        "application/xhtml+xml" to DocumentFormat.HTML,
        "application/pdf" to DocumentFormat.PDF,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to DocumentFormat.DOCX,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to DocumentFormat.XLSX,
        "image/jpeg" to DocumentFormat.JPEG,
        "image/png" to DocumentFormat.PNG,
        "image/gif" to DocumentFormat.GIF,
    )

    private val extensionMap = mapOf(
        "txt" to DocumentFormat.TXT,
        "text" to DocumentFormat.TXT,
        "log" to DocumentFormat.TXT,
        "md" to DocumentFormat.MARKDOWN,
        "markdown" to DocumentFormat.MARKDOWN,
        "html" to DocumentFormat.HTML,
        "htm" to DocumentFormat.HTML,
        "pdf" to DocumentFormat.PDF,
        "docx" to DocumentFormat.DOCX,
        "xlsx" to DocumentFormat.XLSX,
        "jpg" to DocumentFormat.JPEG,
        "jpeg" to DocumentFormat.JPEG,
        "png" to DocumentFormat.PNG,
        "gif" to DocumentFormat.GIF,
    )

    fun detect(mimeType: String?, displayName: String?, header: ByteArray): DocumentFormat {
        val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
        val extension = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val byExtension = extensionMap[extension]
        val byMime = mimeMap[normalizedMime]

        when (detectMagic(header)) {
            Magic.PDF -> return DocumentFormat.PDF
            Magic.PNG -> return DocumentFormat.PNG
            Magic.JPEG -> return DocumentFormat.JPEG
            Magic.GIF -> return DocumentFormat.GIF
            Magic.ZIP -> {
                // A zip container is only docx/xlsx if metadata says which one.
                if (byMime == DocumentFormat.DOCX || byMime == DocumentFormat.XLSX) return byMime
                if (byExtension == DocumentFormat.DOCX || byExtension == DocumentFormat.XLSX) return byExtension
                return DocumentFormat.UNKNOWN
            }
            Magic.HTML -> {
                // Only trust sniffed HTML when metadata is generic; a .md file
                // may legitimately start with an HTML tag.
                if (byMime == null && byExtension == null) return DocumentFormat.HTML
            }
            null -> Unit
        }

        if (byMime != null) {
            if (byMime == DocumentFormat.TXT && byExtension != null) return byExtension
            return byMime
        }
        return byExtension ?: DocumentFormat.UNKNOWN
    }

    private fun detectMagic(header: ByteArray): Magic? {
        if (header.size < 4) return null
        return when {
            header.startsWith(byteArrayOf(0x25, 0x50, 0x44, 0x46)) -> Magic.PDF // %PDF
            header.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) -> Magic.PNG
            header.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) -> Magic.JPEG
            header.startsWith("GIF8".toByteArray()) -> Magic.GIF
            header.startsWith(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) -> Magic.ZIP
            looksLikeHtml(header) -> Magic.HTML
            else -> null
        }
    }

    private fun looksLikeHtml(header: ByteArray): Boolean {
        val text = header.toString(Charsets.UTF_8).trimStart().lowercase()
        return text.startsWith("<!doctype html") || text.startsWith("<html")
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}
