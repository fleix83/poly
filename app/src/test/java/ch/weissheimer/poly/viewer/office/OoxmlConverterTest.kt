package ch.weissheimer.poly.viewer.office

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OoxmlConverterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, String>): File {
        val file = folder.newFile(name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `docx maps headings runs lists and tables`() {
        val documentXml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:r><w:t>Titel</w:t></w:r></w:p>
                <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>fett</w:t></w:r>
                  <w:r><w:t xml:space="preserve"> normal</w:t></w:r></w:p>
                <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr>
                  <w:r><w:t>Listenpunkt</w:t></w:r></w:p>
                <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Zelle A</w:t></w:r></w:p></w:tc>
                  <w:tc><w:p><w:r><w:t>Zelle B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()
        val docx = zip("test.docx", mapOf("word/document.xml" to documentXml))

        val html = DocxToHtml().convert(docx)

        assertTrue(html.contains("<h1>Titel</h1>"))
        assertTrue(html.contains("<strong>fett</strong>"))
        assertTrue(html.contains(" normal"))
        assertTrue(html.contains("<li"))
        assertTrue(html.contains("Listenpunkt"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<td>Zelle A</td>"))
        assertTrue(html.contains("<td>Zelle B</td>"))
    }

    @Test
    fun `xlsx lists sheets and converts cells with shared strings`() {
        val workbook = """
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="Daten" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
        """.trimIndent()
        val rels = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()
        val sharedStrings = """
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <si><t>Name</t></si><si><t>Anna</t></si>
            </sst>
        """.trimIndent()
        val sheet = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1"><v>42</v></c></row>
                <row r="2"><c r="A2" t="s"><v>1</v></c><c r="C2"><v>3.5</v></c></row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        val xlsx = zip(
            "test.xlsx",
            mapOf(
                "xl/workbook.xml" to workbook,
                "xl/_rels/workbook.xml.rels" to rels,
                "xl/sharedStrings.xml" to sharedStrings,
                "xl/worksheets/sheet1.xml" to sheet,
            ),
        )

        val converter = XlsxToHtml()
        val sheets = converter.sheets(xlsx)
        assertEquals(1, sheets.size)
        assertEquals("Daten", sheets.first().name)

        val result = converter.convertSheet(xlsx, sheets.first(), rowLimit = 500)
        assertEquals(2, result.totalRows)
        // Row 1 becomes the sticky header row.
        assertTrue(result.html.contains("<th>Name</th>"))
        assertTrue(result.html.contains("<th>42</th>"))
        assertTrue(result.html.contains("<td>Anna</td>"))
        // Gap column B2 filled before C2 value.
        assertTrue(result.html.contains("<td></td>"))
    }

    @Test
    fun `xlsx row limit truncates and counts all rows`() {
        val workbook = """
            <workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets><sheet name="S" sheetId="1" r:id="rId1"/></sheets>
            </workbook>
        """.trimIndent()
        val rels = """
            <Relationships><Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/></Relationships>
        """.trimIndent()
        val rows = (1..10).joinToString("") { i ->
            """<row r="$i"><c r="A$i"><v>$i</v></c></row>"""
        }
        val xlsx = zip(
            "limit.xlsx",
            mapOf(
                "xl/workbook.xml" to workbook,
                "xl/_rels/workbook.xml.rels" to rels,
                "xl/worksheets/sheet1.xml" to "<worksheet><sheetData>$rows</sheetData></worksheet>",
            ),
        )

        val converter = XlsxToHtml()
        val result = converter.convertSheet(
            xlsx, converter.sheets(xlsx).first(), rowLimit = 3,
            truncatedLabel = "gekürzt", moreLabel = "mehr",
        )
        assertEquals(10, result.totalRows)
        assertEquals(3, result.renderedRows)
        assertTrue(result.html.contains("gekürzt"))
        assertTrue(result.html.contains("Poly.onLoadMoreRows()"))
    }
}
