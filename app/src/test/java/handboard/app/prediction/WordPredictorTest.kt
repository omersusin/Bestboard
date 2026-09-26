package handboard.app.prediction

import org.junit.Assert.*
import org.junit.Test

class WordPredictorTest {

    private fun seeded(): WordPredictor {
        val p = WordPredictor()
        p.seedForTest(
            mapOf("hello" to 50, "help" to 40, "helmet" to 10, "world" to 30),
            mapOf("hello" to mapOf("world" to 5))
        )
        return p
    }

    @Test fun unloadedPredictorReturnsEmpty() {
        assertTrue(WordPredictor().predict("hel", 3).isEmpty())
    }

    @Test fun predictReturnsPrefixMatchesExcludingExact() {
        val p = seeded()
        val got = p.predict("hel", 3)
        assertTrue(got.contains("help"))
        assertFalse(got.any { it == "hel" })
    }

    @Test fun predictFallsBackToFuzzyOnTypo() {
        // Regression: typos with zero prefix hits returned nothing.
        val p = seeded()
        assertTrue(p.predict("hellp", 3).contains("hello"))
    }

    @Test fun predictEmptyPrefixGivesNextWord() {
        val p = seeded()
        p.onWordCommitted("hello")
        // lastWord=hello now; empty prefix -> bigram hello->world
        assertTrue(p.predict("", 3).contains("world"))
    }

    @Test fun autocorrectFixesTypo() {
        val p = seeded()
        assertEquals("hello", p.autocorrect("hellp"))
    }

    @Test fun autocorrectLeavesKnownWordsAlone() {
        // Regression: must never "correct" a valid word.
        val p = seeded()
        assertNull(p.autocorrect("hello"))
    }

    @Test fun autocorrectRejectsShortAndLong() {
        val p = seeded()
        assertNull(p.autocorrect("a"))
        assertNull(p.autocorrect("x".repeat(31)))
    }

    @Test fun commitLearnsWordIntoTrie() {
        val p = seeded()
        p.onWordCommitted("keyboard")
        assertTrue(p.isKnown("keyboard"))
        assertTrue(p.predict("keyb", 3).contains("keyboard"))
    }

    @Test fun commitRejectsPoisonWords() {
        // Regression: one long paste / punctuation could poison trie + fuzzy.
        val p = seeded()
        p.onWordCommitted("x".repeat(31))
        p.onWordCommitted("hello!")
        p.onWordCommitted("a1")
        p.onWordCommitted("a")
        assertFalse(p.isKnown("x".repeat(31)))
        assertFalse(p.isKnown("hello!"))
        assertFalse(p.isKnown("a1"))
    }

    @Test fun bigramLearnedOnCommit() {
        val p = WordPredictor()
        p.seedForTest(mapOf("hello" to 10))
        p.onWordCommitted("hello")
        p.onWordCommitted("newword")
        // "hello" is a complete word with no extensions -> next-word path reads its bigrams.
        assertTrue(p.predict("hello", 3).contains("newword"))
    }

    @Test fun sessionEndClearsLastWord() {
        // Regression: bigrams leaked across editors.
        val p = seeded()
        p.onWordCommitted("hello")
        assertTrue(p.predict("", 3).contains("world"))
        p.onInputSessionEnd()
        assertTrue(p.predict("", 3).isEmpty())
    }

    @Test fun getCurrentWordHandlesEdges() {
        val p = seeded()
        assertEquals("hello", p.getCurrentWord("say hello"))
        assertEquals("", p.getCurrentWord("hello "))
        assertEquals("", p.getCurrentWord(""))
        assertEquals("don't", p.getCurrentWord("don't"))
        assertEquals("e-mail", p.getCurrentWord("e-mail"))
        assertEquals("", p.getCurrentWord("x".repeat(31)))
    }
}
