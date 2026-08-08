package ch.weissheimer.poly.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReAnchorTest {

    private val original = "Der schnelle braune Fuchs springt über den faulen Hund."

    private fun anchorOf(text: String, quoted: String): TextAnchor {
        val start = text.indexOf(quoted)
        return ReAnchor.contextFor(text, start, start + quoted.length)
    }

    @Test
    fun `unchanged text keeps offsets`() {
        val anchor = anchorOf(original, "braune Fuchs")
        val result = ReAnchor.anchor(anchor, original)
        assertNotNull(result)
        assertEquals(anchor.startOffset, result!!.startOffset)
    }

    @Test
    fun `shifted text is found again`() {
        val anchor = anchorOf(original, "braune Fuchs")
        val changed = "NEUER ABSATZ AM ANFANG.\n\n$original"
        val result = ReAnchor.anchor(anchor, changed)
        assertNotNull(result)
        assertEquals(changed.indexOf("braune Fuchs"), result!!.startOffset)
        assertEquals("braune Fuchs", result.quotedText)
    }

    @Test
    fun `ambiguous occurrences resolved by context`() {
        val text = "alpha beta gamma. delta beta omega."
        // Anchor on the second "beta" (context: delta … omega).
        val start = text.lastIndexOf("beta")
        val anchor = ReAnchor.contextFor(text, start, start + 4)
        val changed = "X$text"
        val result = ReAnchor.anchor(anchor, changed)
        assertNotNull(result)
        assertEquals(changed.lastIndexOf("beta"), result!!.startOffset)
    }

    @Test
    fun `removed text orphans the annotation`() {
        val anchor = anchorOf(original, "braune Fuchs")
        val result = ReAnchor.anchor(anchor, "Ein ganz anderer Text ohne die Passage.")
        assertNull(result)
    }

    @Test
    fun `context is limited to 32 chars`() {
        val anchor = anchorOf(original, "springt")
        assert(anchor.prefix.length <= ReAnchor.CONTEXT_LENGTH)
        assert(anchor.suffix.length <= ReAnchor.CONTEXT_LENGTH)
    }
}
