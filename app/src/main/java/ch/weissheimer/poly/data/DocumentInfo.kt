package ch.weissheimer.poly.data

import android.net.Uri
import ch.weissheimer.poly.core.DocumentFormat

data class DocumentInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val format: DocumentFormat,
    val sha256: String,
)
