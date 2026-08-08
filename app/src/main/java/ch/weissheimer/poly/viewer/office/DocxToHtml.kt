package ch.weissheimer.poly.viewer.office

import android.util.Base64
import java.io.File
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Minimal docx → HTML mapping: paragraphs, headings, bold/italic/underline/
 * strikethrough, lists (as bullets with level indent), simple tables,
 * hyperlinks, embedded images as data URIs. No layout fidelity by design.
 */
class DocxToHtml {

    fun convert(docxFile: File): String {
        ZipFile(docxFile).use { zip ->
            val rels = readRelationships(zip, "word/_rels/document.xml.rels")
            val body = StringBuilder()
            val entry = zip.getEntry("word/document.xml")
                ?: throw IllegalArgumentException("word/document.xml missing")
            zip.getInputStream(entry).use { stream ->
                val parser = newParser()
                parser.setInput(stream, null)
                parseBody(parser, body, rels, zip)
            }
            return HtmlTemplates.page(body.toString())
        }
    }

    private fun parseBody(
        parser: XmlPullParser,
        out: StringBuilder,
        rels: Map<String, String>,
        zip: ZipFile,
    ) {
        var listOpen = false
        while (true) {
            val event = try {
                parser.next()
            } catch (e: Exception) {
                break
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event != XmlPullParser.START_TAG) continue
            when (parser.localName()) {
                "p" -> {
                    val paragraph = parseParagraph(parser, rels, zip)
                    if (paragraph.isListItem) {
                        if (!listOpen) {
                            out.append("<ul>")
                            listOpen = true
                        }
                        out.append("<li style=\"margin-left:${paragraph.listLevel * 1.2}em\">")
                            .append(paragraph.html.ifBlank { "&nbsp;" })
                            .append("</li>")
                    } else {
                        if (listOpen) {
                            out.append("</ul>")
                            listOpen = false
                        }
                        val tag = paragraph.headingLevel?.let { "h${it.coerceIn(1, 6)}" } ?: "p"
                        out.append("<$tag>")
                            .append(paragraph.html.ifBlank { "&nbsp;" })
                            .append("</$tag>")
                    }
                }
                "tbl" -> {
                    if (listOpen) {
                        out.append("</ul>")
                        listOpen = false
                    }
                    parseTable(parser, out, rels, zip)
                }
            }
        }
        if (listOpen) out.append("</ul>")
    }

    private class Paragraph(
        val html: String,
        val headingLevel: Int?,
        val isListItem: Boolean,
        val listLevel: Int,
    )

    /** Parser is on <w:p>; consumes until its END_TAG. */
    private fun parseParagraph(
        parser: XmlPullParser,
        rels: Map<String, String>,
        zip: ZipFile,
    ): Paragraph {
        val html = StringBuilder()
        var headingLevel: Int? = null
        var isListItem = false
        var listLevel = 0
        var depth = 1
        var linkHref: String? = null

        while (depth > 0) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG) {
                if (parser.localName() == "hyperlink" && linkHref != null) {
                    html.append("</a>")
                    linkHref = null
                }
                depth--
                continue
            }
            if (event != XmlPullParser.START_TAG) continue
            depth++
            when (parser.localName()) {
                "pStyle" -> {
                    val style = parser.attr("val").orEmpty()
                    headingLevel = HEADING_PATTERN.find(style)?.groupValues?.get(2)?.toIntOrNull()
                    depth-- // self-handled below: pStyle has no children we consume
                    skipElement(parser)
                }
                "numPr" -> {
                    isListItem = true
                    depth--
                    listLevel = parseNumPr(parser)
                }
                "hyperlink" -> {
                    val target = rels[parser.attr("id").orEmpty()]
                    if (target != null) {
                        html.append("<a href=\"").append(escape(target)).append("\">")
                        linkHref = target
                    }
                    // children are runs, keep walking (depth already counted)
                }
                "r" -> {
                    depth--
                    html.append(parseRun(parser, rels, zip))
                }
            }
        }
        return Paragraph(html.toString(), headingLevel, isListItem, listLevel)
    }

    /** Parser is on <w:numPr>; consumes it, returns ilvl. */
    private fun parseNumPr(parser: XmlPullParser): Int {
        var level = 0
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.localName() == "ilvl") {
                        level = parser.attr("val")?.toIntOrNull() ?: 0
                    }
                    depth++
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return level
            }
        }
        return level
    }

    /** Parser is on <w:r>; consumes until its END_TAG, returns run HTML. */
    private fun parseRun(parser: XmlPullParser, rels: Map<String, String>, zip: ZipFile): String {
        var bold = false
        var italic = false
        var underline = false
        var strike = false
        val text = StringBuilder()
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.localName()) {
                        "b" -> if (parser.attr("val") != "false" && parser.attr("val") != "0") bold = true
                        "i" -> if (parser.attr("val") != "false" && parser.attr("val") != "0") italic = true
                        "u" -> if (parser.attr("val") != "none") underline = true
                        "strike" -> if (parser.attr("val") != "false" && parser.attr("val") != "0") strike = true
                        "t" -> {
                            text.append(escape(parser.nextText()))
                            depth--
                        }
                        "br", "cr" -> text.append("<br>")
                        "tab" -> text.append("&emsp;")
                        "blip" -> {
                            val relId = parser.attr("embed") ?: parser.attr("link")
                            val target = relId?.let { rels[it] }
                            if (target != null) {
                                imageDataUri(zip, target)?.let { uri ->
                                    text.append("<img src=\"").append(uri).append("\" alt=\"\">")
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }

        var result = text.toString()
        if (result.isEmpty()) return result
        if (strike) result = "<s>$result</s>"
        if (underline) result = "<u>$result</u>"
        if (italic) result = "<em>$result</em>"
        if (bold) result = "<strong>$result</strong>"
        return result
    }

    /** Parser is on <w:tbl>; consumes it, emits a <table>. */
    private fun parseTable(
        parser: XmlPullParser,
        out: StringBuilder,
        rels: Map<String, String>,
        zip: ZipFile,
    ) {
        out.append("<table>")
        var depth = 1
        var inRow = false
        var cell: StringBuilder? = null
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    when (parser.localName()) {
                        "tr" -> {
                            out.append("<tr>")
                            inRow = true
                            depth++
                        }
                        "tc" -> {
                            cell = StringBuilder()
                            depth++
                        }
                        "p" -> {
                            val paragraph = parseParagraph(parser, rels, zip)
                            val target = cell
                            if (target != null) {
                                if (target.isNotEmpty()) target.append("<br>")
                                target.append(paragraph.html)
                            }
                            // parseParagraph consumed the element: no depth change
                        }
                        else -> depth++
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.localName()) {
                        "tr" -> {
                            if (inRow) out.append("</tr>")
                            inRow = false
                        }
                        "tc" -> {
                            out.append("<td>")
                                .append(cell?.toString()?.ifBlank { "&nbsp;" } ?: "&nbsp;")
                                .append("</td>")
                            cell = null
                        }
                    }
                    depth--
                }
                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }
        out.append("</table>")
    }

    private fun imageDataUri(zip: ZipFile, relTarget: String): String? {
        val path = if (relTarget.startsWith("/")) relTarget.removePrefix("/") else "word/$relTarget"
        val entry = zip.getEntry(path) ?: return null
        if (entry.size > MAX_IMAGE_BYTES) return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val mime = when (path.substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> return null
        }
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private companion object {
        val HEADING_PATTERN = Regex("(?i)(heading|berschrift)(\\d)")
        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    }
}

internal fun newParser(): XmlPullParser {
    val factory = XmlPullParserFactory.newInstance()
    factory.isNamespaceAware = false
    return factory.newPullParser()
}

internal fun XmlPullParser.localName(): String = name.substringAfterLast(':')

/** Attribute lookup ignoring namespace prefixes. */
internal fun XmlPullParser.attr(local: String): String? {
    for (i in 0 until attributeCount) {
        if (getAttributeName(i).substringAfterLast(':') == local) return getAttributeValue(i)
    }
    return null
}

internal fun escape(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(c)
        }
    }
}

/** word/_rels/document.xml.rels → id → target map. */
internal fun readRelationships(zip: ZipFile, path: String): Map<String, String> {
    val entry = zip.getEntry(path) ?: return emptyMap()
    val map = mutableMapOf<String, String>()
    zip.getInputStream(entry).use { stream ->
        val parser = newParser()
        parser.setInput(stream, null)
        while (true) {
            val event = try {
                parser.next()
            } catch (e: Exception) {
                break
            }
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG && parser.localName() == "Relationship") {
                val id = parser.attr("Id") ?: continue
                val target = parser.attr("Target") ?: continue
                map[id] = target
            }
        }
    }
    return map
}
