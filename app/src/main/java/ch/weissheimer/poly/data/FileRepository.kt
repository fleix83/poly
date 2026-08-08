package ch.weissheimer.poly.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import ch.weissheimer.poly.core.FormatDetector
import java.io.FileNotFoundException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Thrown when the persisted URI permission is gone (e.g. after a reboot). */
class DocumentAccessException(cause: Throwable) : Exception(cause)

class FileRepository(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Resolves metadata for [uri]: display name and size from the provider,
     * format from MIME/extension/magic bytes, and a streamed SHA-256 of the
     * content (header bytes are captured from the same single pass).
     */
    suspend fun resolve(uri: Uri): DocumentInfo = withContext(io) {
        val resolver = context.contentResolver
        var displayName: String? = null
        var sizeBytes: Long? = null

        if (uri.scheme == "content") {
            runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) displayName = cursor.getString(nameIdx)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        if (displayName == null) displayName = uri.lastPathSegment?.substringAfterLast('/')

        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()

        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(HEADER_SIZE)
        var headerLength = 0
        var totalBytes = 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (headerLength < HEADER_SIZE) {
                        val copy = minOf(HEADER_SIZE - headerLength, read)
                        System.arraycopy(buffer, 0, header, headerLength, copy)
                        headerLength += copy
                    }
                    digest.update(buffer, 0, read)
                    totalBytes += read
                }
            } ?: throw FileNotFoundException("No stream for $uri")
        } catch (e: SecurityException) {
            throw DocumentAccessException(e)
        }
        if (sizeBytes == null) sizeBytes = totalBytes

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val format = FormatDetector.detect(mimeType, displayName, header.copyOf(headerLength))

        DocumentInfo(
            uri = uri,
            displayName = displayName ?: "?",
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            format = format,
            sha256 = sha256,
        )
    }

    /**
     * Reads the full text content of [uri]. Decodes UTF-8/UTF-16 via BOM when
     * present, otherwise strict UTF-8 with Latin-1 fallback.
     *
     * @throws TextTooLargeException above [MAX_TEXT_BYTES].
     */
    suspend fun openText(uri: Uri): String = withContext(io) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_TEXT_BYTES) throw TextTooLargeException()
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw FileNotFoundException("No stream for $uri")

        decodeText(bytes)
    }

    private fun decodeText(bytes: ByteArray): String {
        // BOM handling first.
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, Charsets.UTF_16BE).removePrefix("\uFEFF")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, Charsets.UTF_16LE).removePrefix("\uFEFF")
        }
        // Strict UTF-8, Latin-1 fallback.
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    /**
     * Keeps read access across restarts where the provider allows it
     * (SAF picker results). VIEW/SEND grants are usually not persistable;
     * that is fine, reopening then falls back to an error state.
     */
    fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    companion object {
        private const val HEADER_SIZE = 64
        const val MAX_TEXT_BYTES = 10L * 1024 * 1024
    }
}

class TextTooLargeException : Exception()
