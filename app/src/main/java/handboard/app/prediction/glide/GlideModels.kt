package handboard.app.prediction.glide

/** One sampled touch point in window coordinates. Pure JVM. */
data class GlidePoint(val x: Float, val y: Float, val t: Long)

/** Key center + half-width in window coordinates. Pure JVM. */
data class KeyRect(val cx: Float, val cy: Float, val hw: Float)

/** Char → geometry registry, filled from Compose layout, read by the decoder. */
class KeyGeometryStore {    private val map = HashMap<Char, KeyRect>(64)

    @Synchronized
    fun set(char: Char, rect: KeyRect) {
        map[char] = rect
    }

    @Synchronized
    fun snapshot(): Map<Char, KeyRect> = HashMap(map)

    @Synchronized
    fun clear() {
        map.clear()
    }
}

/**
 * Shared glide session state: geometry registry, live trail, active flag.
 * Created once per input view via `remember`, passed Service ↔ KeyboardView.
 */
class GlideUiState {
    val geometry = KeyGeometryStore()
    val trail = androidx.compose.runtime.mutableStateListOf<GlidePoint>()
    var active by androidx.compose.runtime.mutableStateOf(false)
}
