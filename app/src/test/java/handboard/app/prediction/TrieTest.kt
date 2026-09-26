package handboard.app.prediction

import org.junit.Assert.*
import org.junit.Test

class TrieTest {

    @Test fun insertAndSearchIsCaseInsensitive() {
        val t = Trie()
        t.insert("Hello", 10)
        assertTrue(t.search("hello"))
        assertTrue(t.search("HELLO"))
        assertFalse(t.search("hell"))
    }

    @Test fun reinsertKeepsMaxFrequency() {
        val t = Trie()
        t.insert("hello", 10)
        t.insert("hello", 5)
        assertEquals(10, t.getFrequency("hello"))
        t.insert("hello", 20)
        assertEquals(20, t.getFrequency("hello"))
    }

    @Test fun updateFrequencyIsAdditive() {
        val t = Trie()
        t.insert("hello", 10)
        t.updateFrequency("hello", 5)
        assertEquals(15, t.getFrequency("hello"))
    }

    @Test fun sizeCountsDistinctWords() {
        val t = Trie()
        t.insert("a", 1)
        t.insert("a", 2)
        t.insert("b", 1)
        assertEquals(2, t.size())
    }

    @Test fun replaceWithResetsCount() {
        // Regression: reload did clear()+putAll() but left wordCount stale.
        val old = Trie()
        repeat(5) { old.insert("w$it", 1) }
        val fresh = Trie()
        fresh.insert("only", 1)
        old.replaceWith(fresh)
        assertEquals(1, old.size())
        assertTrue(old.search("only"))
        assertFalse(old.search("w0"))
    }

    @Test fun wordsWithPrefixSortedAndLimited() {
        val t = Trie()
        t.insert("hello", 5)
        t.insert("help", 10)
        t.insert("helm", 7)
        val got = t.wordsWithPrefix("hel", 2).map { it.first }
        assertEquals(listOf("help", "helm"), got)
    }

    @Test fun wordsWithPrefixMissIsEmpty() {
        val t = Trie()
        t.insert("hello", 5)
        assertTrue(t.wordsWithPrefix("xyz", 3).isEmpty())
    }

    @Test fun fuzzyFindsDeletion() {
        val t = Trie()
        t.insert("hello", 10)
        assertTrue(t.fuzzySearch("helllo", 3).any { it.first == "hello" })
    }

    @Test fun fuzzyFindsSubstitution() {
        val t = Trie()
        t.insert("hello", 10)
        assertTrue(t.fuzzySearch("hallo", 3).any { it.first == "hello" })
    }

    @Test fun fuzzyFindsInsertion() {
        val t = Trie()
        t.insert("hello", 10)
        assertTrue(t.fuzzySearch("helo", 3).any { it.first == "hello" })
    }

    @Test fun fuzzyFindsTransposition() {
        val t = Trie()
        t.insert("the", 10)
        assertTrue(t.fuzzySearch("hte", 3).any { it.first == "the" })
    }

    @Test fun fuzzyShortWordIsEmpty() {
        val t = Trie()
        t.insert("a", 10)
        assertTrue(t.fuzzySearch("a", 3).isEmpty())
    }

    @Test fun fuzzyExcludesExactMatch() {
        val t = Trie()
        t.insert("hello", 10)
        assertFalse(t.fuzzySearch("hello", 3).any { it.first == "hello" })
    }

    @Test fun fuzzyRankedByFrequency() {
        val t = Trie()
        t.insert("hella", 1)
        t.insert("hello", 99)
        assertEquals("hello", t.fuzzySearch("hallo", 2).first().first)
    }
}
