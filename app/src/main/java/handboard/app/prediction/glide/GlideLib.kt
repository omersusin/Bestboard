package handboard.app.prediction.glide

import android.os.Build
import java.io.File

/**
 * Gesture-typing native library loader.
 *
 * v1: the Google gesture decoder (libjni_latinimegoogle.so) ships in
 * app/src/main/jniLibs per ABI and loads via [System.loadLibrary].
 * The checksum table below is for verifying user-supplied replacements
 * (FrostKeys-compatible values) — see [verifyUserLib].
 */
object GlideLib {
    const val LIB_NAME = "jni_latinimegoogle"
    const val USER_LIB_FILE = "libjni_latinimegoogle.so"

    // Verified 2026-09-26 against erkserkserks/openboard jniLibs (actual ELF arch).
    private const val CHECKSUM_ARM64 = "b1049983e6ac5cfc6d1c66e38959751044fad213dff0637a6cf1d2a2703e754f"
    private const val CHECKSUM_ARM32 = "442a2a8bfcb25489564bc9433a916fa4dc0dba9000fe6f6f03f5939b985091e6"
    private const val CHECKSUM_X86_64 = "bd946d126c957b5a6dea3bafa07fa36a27950b30e2b684dffc60746d0a1c7ad8"
    private const val CHECKSUM_X86 = "c882e12e6d48dd946e0b644c66868a720bd11ac3fecf152000e21a3d5abd59c9"

    @Volatile var haveGlideLib = false
        private set

    internal fun expectedChecksum(abi: String): String? = when (abi) {
        "arm64-v8a" -> CHECKSUM_ARM64
        "armeabi-v7a" -> CHECKSUM_ARM32
        "x86_64" -> CHECKSUM_X86_64
        "x86" -> CHECKSUM_X86
        else -> null
    }

    /** Load the bundled decoder, if present for this ABI. Never throws. */
    fun loadBundled() {
        if (haveGlideLib) return
        try {
            System.loadLibrary(LIB_NAME)
            haveGlideLib = true
        } catch (_: UnsatisfiedLinkError) {
            haveGlideLib = false
        } catch (_: Exception) {
            haveGlideLib = false
        }
    }

    /** Load a user-supplied .so after checksum verification. Never throws. */
    fun loadUserLib(dir: File, abi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: ""): Boolean {
        return try {
            val f = File(dir, USER_LIB_FILE)
            if (!f.isFile) return false
            if (sha256(f) != expectedChecksum(abi)) return false
            System.load(f.absolutePath)
            haveGlideLib = true
            true
        } catch (_: Throwable) {
            false
        }
    }

    internal fun sha256(f: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        f.inputStream().use { stream ->
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
