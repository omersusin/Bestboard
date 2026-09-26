package handboard.app.prediction

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class DictionaryInfo(val id: String, val name: String, val isAsset: Boolean, val file: File? = null)

class DictionaryManager(private val context: Context) {

    companion object {
        internal val VALID_WORD_REGEX = Regex("^[\\p{L}]+$")

        // ponytail: pure line parsers extracted for JVM tests (were inline + asset-bound).
        internal fun parseDictLine(line: String): Pair<String, Int>? {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            val word = parts[0].lowercase()
            if (word.length !in 2..30 || !word.matches(VALID_WORD_REGEX)) return null
            val freq = if (parts.size > 1) parts[1].toIntOrNull() ?: 500 else 500
            return word to freq
        }

        internal fun parseBigramLine(line: String): Triple<String, String, Int>? {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 3) return null
            val prev = parts[0].lowercase()
            val next = parts[1].lowercase()
            if (!prev.matches(VALID_WORD_REGEX) || !next.matches(VALID_WORD_REGEX)) return null
            return Triple(prev, next, parts[2].toIntOrNull() ?: 1)
        }

        internal fun loadIntoTrie(reader: BufferedReader, trie: Trie) {
            reader.use { r ->
                r.forEachLine { line -> parseDictLine(line)?.let { (w, f) -> trie.insert(w, f) } }
            }
        }

        internal fun loadBigrams(reader: BufferedReader, bigramMap: HashMap<String, HashMap<String, Int>>) {
            reader.use { r ->
                r.forEachLine { line ->
                    parseBigramLine(line)?.let { (prev, next, count) ->
                        bigramMap.getOrPut(prev) { HashMap() }[next] = count
                    }
                }
            }
        }
    }

    fun getAvailable(): List<DictionaryInfo> {
        val list = mutableListOf<DictionaryInfo>()
        
        try {
            context.assets.list("")?.filter { it.endsWith(".txt") && !it.contains("bigram") }?.forEach { f ->
                val id = f.removeSuffix(".txt")
                val name = when (id) { "en_us" -> "English (US)"; "tr_tr" -> "Türkçe"; else -> id.uppercase() }
                list.add(DictionaryInfo(id, name, true))
            }
        } catch (_: Exception) {}

        val dictDir = File(context.filesDir, "dictionaries")
        if (dictDir.exists()) {
            dictDir.listFiles()?.filter { it.extension == "txt" }?.forEach { f ->
                list.add(DictionaryInfo("ext_${f.nameWithoutExtension}", "Custom: ${f.nameWithoutExtension.takeLast(6)}", false, f))
            }
        }
        return list
    }

    fun loadIntoTrie(dictInfo: DictionaryInfo, trie: Trie) {
        try {
            if (dictInfo.isAsset) {
                loadIntoTrie(BufferedReader(InputStreamReader(context.assets.open("${dictInfo.id}.txt"))), trie)
            } else {
                val f = dictInfo.file ?: return
                loadIntoTrie(f.bufferedReader(), trie)
            }
        } catch (_: Exception) {}
    }

    fun loadBigrams(dictId: String, bigramMap: HashMap<String, HashMap<String, Int>>) {
        if (dictId.startsWith("ext_")) return 
        try {
            loadBigrams(BufferedReader(InputStreamReader(context.assets.open("${dictId}_bigrams.txt"))), bigramMap)
        } catch (_: Exception) {}
    }

    fun deleteCustomDictionaries() {
        val dictDir = File(context.filesDir, "dictionaries")
        if (dictDir.exists()) {
            dictDir.listFiles()?.forEach { it.delete() }
        }
    }
}
