package handboard.app.prediction.glide

import handboard.app.prediction.Trie
import handboard.app.prediction.WordPredictor
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GlideDecoderTest {

    private fun rowKeys(vararg chars: Char): Map<Char, KeyRect> {
        // 100px pitch, 50px half-width — matches decoder REACH_MULT math.
        return chars.mapIndexed { i, c -> c to KeyRect(i * 100f, 0f, 50f) }.toMap()
    }

    private fun dwellTrail(vararg stops: Pair<Float, Int>): List<GlidePoint> {
        // Each stop: (x, sample count). 8px resample keeps dense runs dense.
        val pts = mutableListOf<GlidePoint>()
        var t = 0L
        for ((x, n) in stops) {
            repeat(n) {
                pts.add(GlidePoint(x, 0f, t))
                t += 16L
            }
        }
        return pts
    }

    private fun decoder(vararg words: Pair<String, Int>): TrieGlideDecoder {
        val trie = Trie()
        words.forEach { (w, f) -> trie.insert(w, f) }
        return TrieGlideDecoder(trie::search, trie::getFrequency, trie::wordsWithPrefix)
    }

    @Test fun swipeHelloWithDwellFindsHello() {
        val d = decoder("hello" to 50, "help" to 40, "helm" to 10)
        val keys = rowKeys('h', 'e', 'l', 'o')
        val trail = dwellTrail(0f to 3, 100f to 3, 200f to 14, 300f to 3)
        assertEquals("hello", d.decode(trail, keys, 3).firstOrNull())
    }

    @Test fun shortTapTrailIsEmpty() {
        val d = decoder("hello" to 50)
        val keys = rowKeys('h', 'e', 'l', 'o')
        val trail = dwellTrail(0f to 2)
        assertTrue(d.decode(trail, keys, 3).isEmpty())
    }

    @Test fun unknownPathIsEmpty() {
        val d = decoder("hello" to 50)
        val keys = rowKeys('h', 'e', 'l', 'o', 'x', 'z')
        val trail = dwellTrail(400f to 4, 500f to 4)
        assertTrue(d.decode(trail, keys, 3).isEmpty())
    }

    @Test fun prefixCompletionRanksByFrequency() {
        val d = decoder("help" to 40, "hello" to 50)
        val keys = rowKeys('h', 'e', 'l')
        val trail = dwellTrail(0f to 3, 100f to 3, 200f to 3)
        assertEquals(listOf("hello", "help"), d.decode(trail, keys, 3))
    }

    @Test fun predictorDecodeGlideEndToEnd() {
        val p = WordPredictor()
        p.seedForTest(mapOf("hello" to 50, "help" to 40))
        val keys = rowKeys('h', 'e', 'l', 'o')
        val trail = dwellTrail(0f to 3, 100f to 3, 200f to 14, 300f to 3)
        assertEquals("hello", p.decodeGlide(trail, keys, 3).firstOrNull())
    }

    @Test fun predictorDecodeGlideUnloadedIsEmpty() {
        val keys = rowKeys('h', 'e', 'l', 'o')
        assertTrue(WordPredictor().decodeGlide(dwellTrail(0f to 5, 100f to 5), keys, 3).isEmpty())
    }

    @Test fun geometryStoreRoundTrip() {
        val store = KeyGeometryStore()
        store.set('a', KeyRect(1f, 2f, 3f))
        assertEquals(KeyRect(1f, 2f, 3f), store.snapshot()['a'])
        store.clear()
        assertTrue(store.snapshot().isEmpty())
    }

    @Test fun libChecksumsMatchShippedBinaries() {
        assertEquals("b1049983e6ac5cfc6d1c66e38959751044fad213dff0637a6cf1d2a2703e754f", GlideLib.expectedChecksum("arm64-v8a"))
        assertEquals("442a2a8bfcb25489564bc9433a916fa4dc0dba9000fe6f6f03f5939b985091e6", GlideLib.expectedChecksum("armeabi-v7a"))
        assertEquals("bd946d126c957b5a6dea3bafa07fa36a27950b30e2b684dffc60746d0a1c7ad8", GlideLib.expectedChecksum("x86_64"))
        assertNull(GlideLib.expectedChecksum("mips"))
    }

    @Test fun libSha256KnownVector() {
        val f = File.createTempFile("glide", ".bin")
        try {
            f.writeBytes("abc".toByteArray())
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", GlideLib.sha256(f))
        } finally {
            f.delete()
        }
    }
}
