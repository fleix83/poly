package ch.weissheimer.poly.export

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import androidx.compose.ui.graphics.toArgb
import ch.weissheimer.poly.AppContainer
import ch.weissheimer.poly.annotation.Annotation
import ch.weissheimer.poly.annotation.AnnotationType
import ch.weissheimer.poly.core.DocumentFormat
import ch.weissheimer.poly.data.DocumentInfo
import ch.weissheimer.poly.data.LocalFileCache
import ch.weissheimer.poly.ui.viewer.ViewerState
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportPayload(
    val file: File,
    val mimeType: String,
    val suggestedName: String,
)

class ExportFailedException(cause: Throwable? = null) : Exception(cause)

/**
 * Builds the annotated output file for the current document into the app
 * cache (share/ directory, exposed via FileProvider).
 */
object Exporter {

    suspend fun buildAnnotatedExport(
        context: Context,
        container: AppContainer,
        state: ViewerState,
    ): ExportPayload {
        val document = state.document
        val annotations = state.annotations.annotations.toList()
        val baseName = document.displayName.substringBeforeLast('.').ifBlank { "dokument" }
        val shareDir = File(context.cacheDir, "share").apply { mkdirs() }

        return try {
            when (document.format) {
                DocumentFormat.PDF -> {
                    val target = File(shareDir, "$baseName-markiert.pdf")
                    val source = pdfSource(context, document)
                    withContext(Dispatchers.IO) {
                        target.outputStream().use { out ->
                            PdfAnnotationSaver.save(context, source, annotations, out)
                        }
                    }
                    ExportPayload(target, "application/pdf", "$baseName-markiert.pdf")
                }

                DocumentFormat.TXT -> {
                    val text = container.fileRepository.openText(document.uri)
                    val styled = withHighlightSpans(text, annotations)
                    val target = File(shareDir, "$baseName.pdf")
                    withContext(Dispatchers.IO) {
                        target.outputStream().use { out -> TextPdfExporter.export(styled, out, state.monospace) }
                    }
                    ExportPayload(target, "application/pdf", "$baseName.pdf")
                }

                DocumentFormat.MARKDOWN -> {
                    val markdown = container.fileRepository.openText(document.uri)
                    val rendered = withContext(Dispatchers.Main) {
                        val markwon = Markwon.builder(context)
                            .usePlugin(TablePlugin.create(context))
                            .usePlugin(StrikethroughPlugin.create())
                            .usePlugin(LinkifyPlugin.create())
                            .build()
                        markwon.toMarkdown(markdown)
                    }
                    val styled = withHighlightSpans(rendered, annotations)
                    val target = File(shareDir, "$baseName.pdf")
                    withContext(Dispatchers.IO) {
                        target.outputStream().use { out -> TextPdfExporter.export(styled, out) }
                    }
                    ExportPayload(target, "application/pdf", "$baseName.pdf")
                }

                DocumentFormat.HTML, DocumentFormat.DOCX, DocumentFormat.XLSX -> {
                    val html = state.exportHtmlProvider?.invoke()
                        ?: throw ExportFailedException()
                    val target = File(shareDir, "$baseName.pdf")
                    withContext(Dispatchers.IO) { target.outputStream() }.use { out ->
                        HtmlPdfExporter.export(context, html, out)
                    }
                    ExportPayload(target, "application/pdf", "$baseName.pdf")
                }

                DocumentFormat.JPEG, DocumentFormat.PNG, DocumentFormat.GIF -> {
                    val asPng = document.format != DocumentFormat.JPEG
                    val extension = if (asPng) "png" else "jpg"
                    val target = File(shareDir, "$baseName-markiert.$extension")
                    withContext(Dispatchers.IO) {
                        target.outputStream().use { out ->
                            ImageExporter.flatten(context, document.uri, annotations, out, asPng)
                        }
                    }
                    ExportPayload(
                        target,
                        if (asPng) "image/png" else "image/jpeg",
                        "$baseName-markiert.$extension",
                    )
                }

                DocumentFormat.UNKNOWN -> throw ExportFailedException()
            }
        } catch (e: ExportFailedException) {
            throw e
        } catch (e: Exception) {
            throw ExportFailedException(e)
        }
    }

    /** Local PDF source; prefers the decrypted copy when one exists. */
    fun pdfSource(context: Context, document: DocumentInfo): File {
        val decrypted = File(context.cacheDir, "pdf/${document.sha256}-dec.pdf")
        if (decrypted.length() > 0) return decrypted
        val cached = File(context.cacheDir, "pdf/${document.sha256}.pdf")
        if (cached.length() > 0) return cached
        return LocalFileCache.localCopy(context, document.uri, document.sha256, "pdf")
    }

    private fun withHighlightSpans(text: CharSequence, annotations: List<Annotation>): Spannable {
        val spannable = SpannableString(text)
        for (annotation in annotations) {
            if (annotation.type != AnnotationType.TEXT_HIGHLIGHT) continue
            val anchor = annotation.anchor ?: continue
            if (anchor.startOffset >= anchor.endOffset || anchor.endOffset > spannable.length) continue
            spannable.setSpan(
                BackgroundColorSpan(annotation.color.color.copy(alpha = 0.4f).toArgb()),
                anchor.startOffset,
                anchor.endOffset,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return spannable
    }
}
