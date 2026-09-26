package handboard.app.prediction

import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class DictionaryManagerParserTest {

    @Test fun dictLineBasicAndFreq() {
        assertEquals("hello" to 500, DictionaryManager.parseDictLine("hello\t500"))
        assertEquals("hello" to 500, DictionaryManager.parseDictLine("hello"))
        assertEquals("hello" to 500, DictionaryManager.parseDictLine("hello\tabc"))
        assertEquals("hello" to 500, DictionaryManager.parseDictLine("  Hello  "))
    }

    @Test fun dictLineRejectsBadWords() {
        assertNull(DictionaryManager.parseDictLine(""))
        assertNull(DictionaryManager.parseDictLine("   "))
        assertNull(DictionaryManager.parseDictLine("a"))
        assertNull(DictionaryManager.parseDictLine("x".repeat(31)))
        assertNull(DictionaryManager.parseDictLine("hello1"))
    }

    @Test fun bigramLineBasic() {
        assertEquals(Triple("hello", "world", 3), DictionaryManager.parseBigramLine("hello world 3"))
        assertEquals(Triple("a", "b", 1), DictionaryManager.parseBigramLine("A B xyz"))
        assertNull(DictionaryManager.parseBigramLine("hello world"))
        assertNull(DictionaryManager.parseBigramLine("hello1 world 3"))
    }

    @Test fun loadIntoTrieFromReader() {
        val trie = Trie()
        DictionaryManager.loadIntoTrie(
            BufferedReader(StringReader("hello\t100\nbad1\nok\t200\n")),
            trie
        )
        assertTrue(trie.search("hello"))
        assertTrue(trie.search("ok"))
        assertFalse(trie.search("bad1"))
        assertEquals(100, trie.getFrequency("hello"))
    }

    @Test fun loadBigramsFromReader() {
        val map = HashMap<String, HashMap<String, Int>>()
        DictionaryManager.loadBigrams(
            BufferedReader(StringReader("hello world 3\nh1 world 2\n")),
            map
        )
        assertEquals(3, map["hello"]?.get("world"))
        assertNull(map["h1"])
    }
}
