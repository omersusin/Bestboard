package handboard.app.prediction

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class AospDictConverterParserTest {

    @Test fun plainTextDetection() {
        assertTrue(AospDictConverter.isPlainText("hello world\nfoo bar".toByteArray()))
        assertFalse(AospDictConverter.isPlainText(ByteArray(0)))
        assertFalse(AospDictConverter.isPlainText(byteArrayOf(0x68, 0x00, 0x69)))
    }

    @Test fun decodeAscii() {
        val (cp, len) = AospDictConverter.decodeUtf8Char(byteArrayOf(0x41), 0)!!
        assertEquals('A'.code, cp)
        assertEquals(1, len)
    }

    @Test fun decodeMultibyte() {
        // € = U+20AC (3 bytes), 😀 = U+1F600 (4 bytes)
        assertEquals(0x20AC, AospDictConverter.decodeUtf8Char(byteArrayOf(0xE2.toByte(), 0x82.toByte(), 0xAC.toByte()), 0)?.first)
        assertEquals(0x1F600, AospDictConverter.decodeUtf8Char(byteArrayOf(0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte()), 0)?.first)
    }

    @Test fun decodeRejectsOverlongSurrogateTruncated() {
        assertNull(AospDictConverter.decodeUtf8Char(byteArrayOf(0xC0.toByte(), 0xAF.toByte()), 0))
        assertNull(AospDictConverter.decodeUtf8Char(byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()), 0))
        assertNull(AospDictConverter.decodeUtf8Char(byteArrayOf(0xE2.toByte()), 0))
    }

    @Test fun scavengeSplitsDedupsAndBounds() = runBlocking {
        val words = AospDictConverter.scavengeWords("hello,world! hello a ${"x".repeat(31)} 123".toByteArray())
        assertTrue(words.contains("hello"))
        assertTrue(words.contains("world"))
        assertEquals(1, words.count { it == "hello" })
        assertFalse(words.any { it == "a" })
        assertFalse(words.any { it.length > 30 })
        assertFalse(words.any { it.any(Char::isDigit) })
    }

    @Test fun readBytesEnforcesCap() {
        // Regression: unbounded readBytes OOM'd the keyboard process.
        val big = ByteArray(AospDictConverter.MAX_IMPORT_BYTES + 1)
        try {
            AospDictConverter.readBytes(ByteArrayInputStream(big))
            fail("expected IllegalStateException over 5MB")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("5MB"))
        }
    }

    @Test fun readBytesPassesSmallStream() {
        val data = "hello".toByteArray()
        assertArrayEquals(data, AospDictConverter.readBytes(ByteArrayInputStream(data)))
    }
}
