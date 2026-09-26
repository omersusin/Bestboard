package handboard.app.prediction

import android.content.Context
import android.util.Log
import handboard.app.prediction.glide.GlidePoint
import handboard.app.prediction.glide.KeyRect
import handboard.app.prediction.glide.TrieGlideDecoder

class WordPredictor {
    private val trie = Trie()
    private val bigramMap = HashMap<String, HashMap<String, Int>>()
    private var lastWord = ""
    @Volatile private var isLoaded = false
    private var personalDict: PersonalDictionary? = null
    private var dictManager: DictionaryManager? = null

    private var lastPrefix = ""
    private var lastResults = emptyList<String>()

    @Synchronized
    fun loadDictionaries(context: Context, dictIds: Set<String>) {
        val newTrie = Trie()
        // ponytail: build bigrams off-thread into a local map, swap under lock (was clear+fill shared map).
        val newBigrams = HashMap<String, HashMap<String, Int>>()
        dictManager = DictionaryManager(context)
        dictIds.forEach { dictId ->
            val dict = dictManager?.getAvailable()?.find { it.id == dictId }
            if (dict != null) dictManager?.loadIntoTrie(dict, newTrie)
            dictManager?.loadBigrams(dictId, newBigrams)
        }
        personalDict = PersonalDictionary(context)
        personalDict?.applyToTrie(newTrie)
        personalDict?.applyBigrams(newBigrams)
        if (newTrie.size() == 0) loadFallback(newTrie)
        trie.replaceWith(newTrie)
        synchronized(bigramMap) { bigramMap.clear(); bigramMap.putAll(newBigrams) }
        isLoaded = true
        lastPrefix = ""
        lastResults = emptyList()
    }

    private fun loadFallback(targetTrie: Trie) {
        val words = listOf("the" to 999,"be" to 998,"to" to 997,"and" to 995, "you" to 982,"hello" to 899,"thanks" to 898,"yes" to 894)
        words.forEach { (w, f) -> targetTrie.insert(w, f) }
    }

    fun predict(textBeforeCursor: String, maxSuggestions: Int = 3): List<String> {
        if (!isLoaded || trie.size() == 0) return emptyList()
        val prefix = extractCurrentWord(textBeforeCursor)

        if (prefix.isEmpty() || prefix.length > 30) return predictNextWord(maxSuggestions)
        if (prefix == lastPrefix && lastResults.isNotEmpty()) return lastResults.take(maxSuggestions)

        val results = trie.wordsWithPrefix(prefix, maxSuggestions + 3).map { it.first }.filter { it != prefix }
        // ponytail: fall back to 1-edit fuzzy when no prefix match (typo). Trie.fuzzySearch already exists.
        val final = if (results.isEmpty() && !trie.search(prefix)) {
            trie.fuzzySearch(prefix, maxSuggestions).map { it.first }
        } else results
        lastPrefix = prefix
        lastResults = final

        if (trie.search(prefix) && final.isEmpty()) return predictNextWord(maxSuggestions, prefix)
        return final.take(maxSuggestions)
    }

    fun isKnown(word: String): Boolean = trie.search(word.lowercase().trim())

    fun autocorrect(word: String): String? {
        val clean = word.lowercase().trim()
        if (clean.length < 2 || clean.length > 30) return null
        if (trie.search(clean)) return null
        return trie.fuzzySearch(clean, 1).firstOrNull()?.first
    }

    private fun predictNextWord(limit: Int, overrideLastWord: String? = null): List<String> {
        val word = overrideLastWord ?: lastWord
        if (word.isEmpty()) return emptyList()
        synchronized(bigramMap) {
            return bigramMap[word.lowercase()]?.entries?.sortedByDescending { it.value }?.take(limit)?.map { it.key } ?: emptyList()
        }
    }

    fun onWordCommitted(word: String) {
        val lower = word.lowercase().trim()
        // ponytail: cap length/charset (was <2 only — one long paste could poison trie + fuzzy).
        if (lower.length < 2 || lower.length > 30 || !lower.all { it.isLetter() || it == '\'' || it == '-' }) return
        personalDict?.learnWord(lower)
        trie.updateFrequency(lower, 5)
        synchronized(bigramMap) {
            if (lastWord.isNotEmpty()) {
                val count = bigramMap.getOrPut(lastWord) { HashMap() }[lower] ?: 0
                bigramMap[lastWord]!![lower] = count + 1
                personalDict?.learnBigram(lastWord, lower)
            }
        }
        lastWord = lower
        lastPrefix = ""
    }

    /** Called when the input session ends so bigrams don't leak across editors. */
    fun onInputSessionEnd() { lastWord = ""; lastPrefix = ""; lastResults = emptyList() }

    private fun extractCurrentWord(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var endIndex = text.length
        if (!text[endIndex - 1].isLetter() && text[endIndex - 1] != '\'' && text[endIndex - 1] != '-') return ""
        var startIndex = endIndex - 1
        while (startIndex > 0) {
            val ch = text[startIndex - 1]
            if (ch.isLetter() || ch == '\'' || ch == '-') startIndex--
            else break
        }
        val word = text.substring(startIndex, endIndex)
        return if (word.length > 30) "" else word.lowercase()
    }

    /** Swipe-to-word decode over the loaded dictionaries. Empty when unloaded. */
    fun decodeGlide(trail: List<GlidePoint>, keys: Map<Char, KeyRect>, limit: Int = 3): List<String> {
        if (!isLoaded || trie.size() == 0) return emptyList()
        return TrieGlideDecoder(trie::search, trie::getFrequency, trie::wordsWithPrefix).decode(trail, keys, limit)
    }

    /** Test seam: seed an in-memory dictionary without Android assets. */
    internal fun seedForTest(words: Map<String, Int>, bigrams: Map<String, Map<String, Int>> = emptyMap()) {
        words.forEach { (w, f) -> trie.insert(w, f) }
        synchronized(bigramMap) {
            bigrams.forEach { (prev, nexts) ->
                bigramMap.getOrPut(prev) { HashMap() }.putAll(nexts)
            }
        }
        isLoaded = true
    }

    fun getCurrentWord(textBeforeCursor: String): String = extractCurrentWord(textBeforeCursor)
    fun getDictionarySize(): Int = trie.size()
    fun getPersonalWords(): Map<String, Int> = personalDict?.getWords() ?: emptyMap()
    fun clearPersonalDictionary() { personalDict?.clear(); lastPrefix = "" }
}
