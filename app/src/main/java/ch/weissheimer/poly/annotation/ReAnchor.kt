package ch.weissheimer.poly.annotation

/**
 * Re-anchors text highlights after the underlying file changed: quotedText
 * occurrences are scored by surrounding context (prefix/suffix) and proximity
 * to the original offset. No match → orphaned.
 */
object ReAnchor {

    const val CONTEXT_LENGTH = 32

    fun contextFor(text: String, start: Int, end: Int): TextAnchor {
        val prefix = text.substring((start - CONTEXT_LENGTH).coerceAtLeast(0), start)
        val suffix = text.substring(end, (end + CONTEXT_LENGTH).coerceAtMost(text.length))
        return TextAnchor(
            startOffset = start,
            endOffset = end,
            quotedText = text.substring(start, end),
            prefix = prefix,
            suffix = suffix,
        )
    }

    /** @return updated anchor with valid offsets in [text], or null if orphaned. */
    fun anchor(anchor: TextAnchor, text: String): TextAnchor? {
        val quoted = anchor.quotedText
        if (quoted.isEmpty()) return null

        // Fast path: unchanged position.
        if (anchor.endOffset <= text.length &&
            text.regionMatches(anchor.startOffset, quoted, 0, quoted.length)
        ) {
            return anchor
        }

        var best: Int = -1
        var bestScore = -1
        var searchFrom = 0
        while (true) {
            val found = text.indexOf(quoted, searchFrom)
            if (found < 0) break
            val score = contextScore(text, found, found + quoted.length, anchor)
            val closer = best < 0 ||
                score > bestScore ||
                (score == bestScore &&
                    kotlin.math.abs(found - anchor.startOffset) < kotlin.math.abs(best - anchor.startOffset))
            if (closer) {
                best = found
                bestScore = score
            }
            searchFrom = found + 1
        }
        if (best < 0) return null
        return contextFor(text, best, best + quoted.length)
    }

    /** Longest matching context on both sides, in characters. */
    private fun contextScore(text: String, start: Int, end: Int, anchor: TextAnchor): Int {
        var score = 0
        val prefix = anchor.prefix
        var i = 0
        while (i < prefix.length && start - 1 - i >= 0 &&
            text[start - 1 - i] == prefix[prefix.length - 1 - i]
        ) {
            score++
            i++
        }
        val suffix = anchor.suffix
        var j = 0
        while (j < suffix.length && end + j < text.length && text[end + j] == suffix[j]) {
            score++
            j++
        }
        return score
    }
}
