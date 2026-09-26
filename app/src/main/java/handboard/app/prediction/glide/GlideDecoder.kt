package handboard.app.prediction.glide

import kotlin.math.hypot

/**
 * Swipe-to-word decoder over an existing word Trie.
 *
 * v1 is a pure-Kotlin spatial decoder (nearest-key collapse + 1-edit
 * variants + prefix completions). It implements [GlideDecoder] so the
 * native latinime gesture decoder can replace it without touching callers.
 * Pure JVM — fully unit-tested.
 */
interface GlideDecoder {
    fun decode(trail: List<GlidePoint>, keys: Map<Char, KeyRect>, limit: Int): List<String>
}

class TrieGlideDecoder(
    private val isKnown: (String) -> Boolean,
    private val frequencyOf: (String) -> Int,
    private val withPrefix: (String, Int) -> List<Pair<String, Int>>
) : GlideDecoder {

    override fun decode(trail: List<GlidePoint>, keys: Map<Char, KeyRect>, limit: Int): List<String> {
        if (trail.size < 2 || keys.isEmpty()) return emptyList()
        val raw = collapseToChars(trail, keys)
        if (raw.toSet().size < 2) return emptyList()

        val scored = HashMap<String, Int>()
        fun consider(word: String, score: Int) {
            if (word.length < 2) return
            scored[word] = maxOf(scored[word] ?: 0, score)
        }

        if (isKnown(raw)) consider(raw, frequencyOf(raw) * 2)
        // Deletion variants (overshoot / doubled letters).
        for (i in raw.indices) {
            val v = raw.removeRange(i, i + 1)
            if (v.length >= 2 && isKnown(v)) consider(v, frequencyOf(v))
        }
        // Adjacent swaps (transposed path).
        for (i in 0 until raw.length - 1) {
            val arr = raw.toCharArray()
            val tmp = arr[i]; arr[i] = arr[i + 1]; arr[i + 1] = tmp
            val v = String(arr)
            if (isKnown(v)) consider(v, frequencyOf(v))
        }
        // Under-specified trails: complete the prefix.
        withPrefix(raw, limit).forEach { (w, f) -> if (w != raw) consider(w, f) }

        return scored.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    private fun collapseToChars(points: List<GlidePoint>, keys: Map<Char, KeyRect>): String {
        val sb = StringBuilder()
        var runChar: Char? = null
        var runStart = 0L
        for (p in points) {
            var best: Char? = null
            var bestDist = Float.MAX_VALUE
            var bestHw = 0f
            for ((ch, r) in keys) {
                val d = hypot(p.x - r.cx, p.y - r.cy)
                if (d < bestDist) {
                    bestDist = d
                    best = ch
                    bestHw = r.hw
                }
            }
            // Ignore far-off samples (finger left the keyboard) — they break the run.
            val hit = if (best != null && bestDist <= bestHw * REACH_MULT) best else null
            if (hit != runChar) {
                runChar?.let {
                    sb.append(it)
                    // ponytail: lingering on a key (slow pass) means a doubled letter — hello, not helo.
                    if (p.t - runStart >= DWELL_MS) sb.append(it)
                }
                runChar = hit
                runStart = p.t
            }
        }
        runChar?.let {
            sb.append(it)
            if (points.last().t - runStart >= DWELL_MS) sb.append(it)
        }
        return sb.toString()
    }

    companion object {
        internal const val REACH_MULT = 2.5f
        internal const val DWELL_MS = 200L
    }
}
