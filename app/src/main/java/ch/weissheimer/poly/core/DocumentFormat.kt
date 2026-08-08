package ch.weissheimer.poly.core

import androidx.annotation.StringRes
import ch.weissheimer.poly.R

enum class DocumentFormat(@StringRes val labelRes: Int) {
    TXT(R.string.format_txt),
    MARKDOWN(R.string.format_md),
    PDF(R.string.format_pdf),
    DOCX(R.string.format_docx),
    XLSX(R.string.format_xlsx),
    HTML(R.string.format_html),
    JPEG(R.string.format_image),
    PNG(R.string.format_image),
    GIF(R.string.format_image),
    UNKNOWN(R.string.format_unknown);

    val isImage: Boolean
        get() = this == JPEG || this == PNG || this == GIF
}
