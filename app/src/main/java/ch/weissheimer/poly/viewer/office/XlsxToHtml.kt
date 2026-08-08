package ch.weissheimer.poly.viewer.office

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

class SheetInfo(val name: String, val relId: String)

class SheetHtml(
    val html: String,
    val totalRows: Int,
    val renderedRows: Int,
)

/**
 * Minimal xlsx → HTML table. Streams the worksheet XML, stops materializing
 * cells after [rowLimit] rows but keeps counting for the "load more" banner.
 * Number formats are mapped coarsely: date, percent, decimal.
 */
class XlsxToHtml {

    fun sheets(xlsxFile: File): List<SheetInfo> {
        ZipFile(xlsxFile).use { zip ->
            val entry = zip.getEntry("xl/workbook.xml") ?: return emptyList()
            val result = mutableListOf<SheetInfo>()
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
                    if (event == XmlPullParser.START_TAG && parser.localName() == "sheet") {
                        val name = parser.attr("name") ?: "?"
                        val relId = parser.attr("id") ?: continue
                        result.add(SheetInfo(name, relId))
                    }
                }
            }
            return result
        }
    }

    fun convertSheet(
        xlsxFile: File,
        sheet: SheetInfo,
        rowLimit: Int,
        truncatedLabel: String = "",
        moreLabel: String = "",
    ): SheetHtml {
        ZipFile(xlsxFile).use { zip ->
            val rels = readRelationships(zip, "xl/_rels/workbook.xml.rels")
            val target = rels[sheet.relId] ?: throw IllegalArgumentException("sheet target missing")
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val sharedStrings = readSharedStrings(zip)
            val styles = readCellStyles(zip)

            val entry = zip.getEntry(path) ?: throw IllegalArgumentException("$path missing")
            val body = StringBuilder("<table class=\"sheet\">")
            var totalRows = 0
            var renderedRows = 0

            zip.getInputStream(entry).use { stream ->
                val parser = newParser()
                parser.setInput(stream, null)
                var inRow = false
                var columnIndex = 0
                var cellStyle: Int? = null
                var cellType: String? = null
                var cellColumn = 0
                var pendingValue: String? = null
                var pendingInline: String? = null

                fun flushCell(row: StringBuilder) {
                    val display = formatCell(pendingValue, pendingInline, cellType, cellStyle, sharedStrings, styles)
                    while (columnIndex < cellColumn) {
                        row.append("<td></td>")
                        columnIndex++
                    }
                    row.append("<td>").append(display).append("</td>")
                    columnIndex++
                    pendingValue = null
                    pendingInline = null
                }

                var rowBuilder: StringBuilder? = null
                while (true) {
                    val event = try {
                        parser.next()
                    } catch (e: Exception) {
                        break
                    }
                    if (event == XmlPullParser.END_DOCUMENT) break
                    when (event) {
                        XmlPullParser.START_TAG -> when (parser.localName()) {
                            "row" -> {
                                totalRows++
                                inRow = totalRows <= rowLimit
                                if (inRow) {
                                    rowBuilder = StringBuilder()
                                    columnIndex = 0
                                }
                            }
                            "c" -> if (inRow) {
                                cellColumn = parser.attr("r")?.let { columnOf(it) } ?: columnIndex
                                cellStyle = parser.attr("s")?.toIntOrNull()
                                cellType = parser.attr("t")
                            }
                            "v" -> if (inRow) pendingValue = parser.nextText()
                            "t" -> if (inRow && cellType == "inlineStr") {
                                pendingInline = (pendingInline ?: "") + parser.nextText()
                            }
                        }
                        XmlPullParser.END_TAG -> when (parser.localName()) {
                            "c" -> if (inRow) rowBuilder?.let { flushCell(it) }
                            "row" -> if (inRow) {
                                val cells = rowBuilder?.toString().orEmpty()
                                if (totalRows == 1) {
                                    body.append("<thead><tr>")
                                        .append(cells.replace("<td>", "<th>").replace("</td>", "</th>"))
                                        .append("</tr></thead><tbody>")
                                } else {
                                    if (renderedRows == 0 && totalRows > 1) body.append("<tbody>")
                                    body.append("<tr>").append(cells).append("</tr>")
                                }
                                renderedRows++
                                rowBuilder = null
                            }
                        }
                    }
                }
            }
            body.append("</tbody></table>")

            if (totalRows > rowLimit) {
                body.append("<div class=\"more\"><span>")
                    .append(escape(truncatedLabel))
                    .append("</span> <button onclick=\"Poly.onLoadMoreRows()\">")
                    .append(escape(moreLabel))
                    .append("</button></div>")
            }
            return SheetHtml(
                html = HtmlTemplates.page(body.toString(), wide = true),
                totalRows = totalRows,
                renderedRows = renderedRows,
            )
        }
    }

    private fun formatCell(
        value: String?,
        inline: String?,
        type: String?,
        styleIndex: Int?,
        sharedStrings: List<String>,
        styles: List<NumberKind>,
    ): String {
        if (inline != null) return escape(inline)
        if (value == null) return ""
        return when (type) {
            "s" -> escape(value.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: "")
            "b" -> if (value == "1") "TRUE" else "FALSE"
            "str", "e" -> escape(value)
            else -> {
                val number = value.toDoubleOrNull() ?: return escape(value)
                val kind = styleIndex?.let { styles.getOrNull(it) } ?: NumberKind.PLAIN
                escape(formatNumber(number, kind))
            }
        }
    }

    private fun formatNumber(number: Double, kind: NumberKind): String = when (kind) {
        NumberKind.DATE -> excelDate(number)
        NumberKind.PERCENT -> {
            val pct = number * 100
            if (pct == pct.toLong().toDouble()) "${pct.toLong()} %" else "%.2f %%".format(pct)
        }
        NumberKind.PLAIN -> {
            if (number == number.toLong().toDouble() && kotlin.math.abs(number) < 1e15) {
                number.toLong().toString()
            } else {
                "%.6f".format(number).trimEnd('0').trimEnd('.').trimEnd(',')
            }
        }
    }

    enum class NumberKind { PLAIN, DATE, PERCENT }

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val strings = mutableListOf<String>()
        zip.getInputStream(entry).use { stream ->
            val parser = newParser()
            parser.setInput(stream, null)
            var current: StringBuilder? = null
            while (true) {
                val event = try {
                    parser.next()
                } catch (e: Exception) {
                    break
                }
                if (event == XmlPullParser.END_DOCUMENT) break
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.localName()) {
                        "si" -> current = StringBuilder()
                        "t" -> current?.append(parser.nextText())
                    }
                    XmlPullParser.END_TAG -> if (parser.localName() == "si") {
                        strings.add(current?.toString().orEmpty())
                        current = null
                    }
                }
            }
        }
        return strings
    }

    /** cellXfs order → coarse number kind, via numFmtId (builtin + custom codes). */
    private fun readCellStyles(zip: ZipFile): List<NumberKind> {
        val entry = zip.getEntry("xl/styles.xml") ?: return emptyList()
        val customFormats = mutableMapOf<Int, NumberKind>()
        val kinds = mutableListOf<NumberKind>()
        zip.getInputStream(entry).use { stream ->
            val parser = newParser()
            parser.setInput(stream, null)
            var inCellXfs = false
            while (true) {
                val event = try {
                    parser.next()
                } catch (e: Exception) {
                    break
                }
                if (event == XmlPullParser.END_DOCUMENT) break
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.localName()) {
                        "numFmt" -> {
                            val id = parser.attr("numFmtId")?.toIntOrNull() ?: continue
                            val code = parser.attr("formatCode").orEmpty().lowercase()
                            customFormats[id] = when {
                                code.contains('%') -> NumberKind.PERCENT
                                code.any { it == 'y' || it == 'd' } &&
                                    (code.contains('m') || code.contains('j')) -> NumberKind.DATE
                                code.contains('h') && code.contains("mm") -> NumberKind.DATE
                                else -> NumberKind.PLAIN
                            }
                        }
                        "cellXfs" -> inCellXfs = true
                        "xf" -> if (inCellXfs) {
                            val fmtId = parser.attr("numFmtId")?.toIntOrNull() ?: 0
                            kinds.add(numberKind(fmtId, customFormats))
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.localName() == "cellXfs") inCellXfs = false
                }
            }
        }
        return kinds
    }

    private fun numberKind(fmtId: Int, custom: Map<Int, NumberKind>): NumberKind = when {
        fmtId in 14..22 || fmtId in 45..47 -> NumberKind.DATE
        fmtId == 9 || fmtId == 10 -> NumberKind.PERCENT
        else -> custom[fmtId] ?: NumberKind.PLAIN
    }

    companion object {
        /** "BC12" → zero-based column index. */
        fun columnOf(cellRef: String): Int {
            var column = 0
            for (c in cellRef) {
                if (!c.isLetter()) break
                column = column * 26 + (c.uppercaseChar() - 'A' + 1)
            }
            return (column - 1).coerceAtLeast(0)
        }

        /** Excel serial (1900 system) → readable date/time. */
        fun excelDate(serial: Double): String {
            val days = serial.toLong()
            val dayFraction = serial - days
            val date = LocalDate.of(1899, 12, 30).plusDays(days)
            return if (dayFraction > 1e-6) {
                val seconds = Math.round(dayFraction * 24 * 60 * 60)
                LocalDateTime.of(date, java.time.LocalTime.ofSecondOfDay(seconds % 86_400))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            } else {
                date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            }
        }
    }
}
