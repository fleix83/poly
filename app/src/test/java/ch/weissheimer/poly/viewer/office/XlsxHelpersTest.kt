package ch.weissheimer.poly.viewer.office

import org.junit.Assert.assertEquals
import org.junit.Test

class XlsxHelpersTest {

    @Test
    fun `column reference parsing`() {
        assertEquals(0, XlsxToHtml.columnOf("A1"))
        assertEquals(1, XlsxToHtml.columnOf("B12"))
        assertEquals(25, XlsxToHtml.columnOf("Z3"))
        assertEquals(26, XlsxToHtml.columnOf("AA1"))
        assertEquals(27, XlsxToHtml.columnOf("AB99"))
        assertEquals(54, XlsxToHtml.columnOf("BC12"))
    }

    @Test
    fun `excel serial date conversion`() {
        assertEquals("01.01.2020", XlsxToHtml.excelDate(43831.0))
        assertEquals("31.12.1999", XlsxToHtml.excelDate(36525.0))
        // Half a day = noon.
        assertEquals("01.01.2020 12:00", XlsxToHtml.excelDate(43831.5))
    }
}
