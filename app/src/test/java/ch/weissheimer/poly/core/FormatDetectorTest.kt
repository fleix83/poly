package ch.weissheimer.poly.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDetectorTest {

    private val pdfHeader = "%PDF-1.7".toByteArray()
    private val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
    private val textHeader = "Hello world".toByteArray()

    @Test
    fun `mime type wins for plain formats`() {
        assertEquals(
            DocumentFormat.MARKDOWN,
            FormatDetector.detect("text/markdown", "readme.txt", textHeader),
        )
    }

    @Test
    fun `extension beats generic text plain mime`() {
        assertEquals(
            DocumentFormat.MARKDOWN,
            FormatDetector.detect("text/plain", "notes.md", textHeader),
        )
    }

    @Test
    fun `magic bytes override wrong extension for binary formats`() {
        assertEquals(
            DocumentFormat.PDF,
            FormatDetector.detect("application/octet-stream", "scan.txt", pdfHeader),
        )
        assertEquals(
            DocumentFormat.PNG,
            FormatDetector.detect(null, "picture.jpg", pngHeader),
        )
    }

    @Test
    fun `zip container resolves via extension`() {
        assertEquals(
            DocumentFormat.DOCX,
            FormatDetector.detect("application/octet-stream", "bewerbung.docx", zipHeader),
        )
        assertEquals(
            DocumentFormat.XLSX,
            FormatDetector.detect(null, "tabelle.xlsx", zipHeader),
        )
        assertEquals(
            DocumentFormat.UNKNOWN,
            FormatDetector.detect(null, "archive.zip", zipHeader),
        )
    }

    @Test
    fun `octet stream with md extension is markdown`() {
        assertEquals(
            DocumentFormat.MARKDOWN,
            FormatDetector.detect("application/octet-stream", "notes.md", textHeader),
        )
    }

    @Test
    fun `html sniffing only without metadata`() {
        assertEquals(
            DocumentFormat.HTML,
            FormatDetector.detect(null, "download", "<!DOCTYPE html><html>".toByteArray()),
        )
        assertEquals(
            DocumentFormat.MARKDOWN,
            FormatDetector.detect(null, "page.md", "<html>".toByteArray()),
        )
    }

    @Test
    fun `unknown when nothing matches`() {
        assertEquals(
            DocumentFormat.UNKNOWN,
            FormatDetector.detect(null, "data.bin", textHeader),
        )
    }
}
