package allyouneed.net.http

import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolClassifierTest {
    @Test
    fun emptyNeedsMore() {
        assertEquals(ProtocolKind.NEED_MORE, classify(*intArrayOf()))
    }

    @Test
    fun httpMethods() {
        assertEquals(ProtocolKind.HTTP, classify("GET / HTTP/1.1\r\n"))
        assertEquals(ProtocolKind.HTTP, classify("POST /foo"))
        assertEquals(ProtocolKind.HTTP, classify("HEAD /"))
        assertEquals(ProtocolKind.HTTP, classify("PUT /"))
        assertEquals(ProtocolKind.HTTP, classify("PATCH /"))
        assertEquals(ProtocolKind.HTTP, classify("DELETE /"))
        assertEquals(ProtocolKind.HTTP, classify("OPTIONS /"))
        assertEquals(ProtocolKind.HTTP, classify("CONNECT host:443"))
        assertEquals(ProtocolKind.HTTP, classify("TRACE /"))
        assertEquals(ProtocolKind.HTTP, classify("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"))
    }

    @Test
    fun httpPartialPrefixWaits() {
        assertEquals(ProtocolKind.NEED_MORE, classify("G"))
        assertEquals(ProtocolKind.NEED_MORE, classify("GE"))
        assertEquals(ProtocolKind.NEED_MORE, classify("GET"))
        assertEquals(ProtocolKind.NEED_MORE, classify("P"))
        assertEquals(ProtocolKind.NEED_MORE, classify("PO"))
        assertEquals(ProtocolKind.NEED_MORE, classify("PRI"))
    }

    @Test
    fun httpLookalikeIsMinecraft() {
        assertEquals(ProtocolKind.MINECRAFT, classify("GXYZ"))
        assertEquals(ProtocolKind.MINECRAFT, classify("PX"))
    }

    @Test
    fun tlsClientHello() {
        assertEquals(ProtocolKind.TLS, classify(0x16, 0x03, 0x01, 0x00, 0x20))
        assertEquals(ProtocolKind.TLS, classify(0x16, 0x03, 0x03, 0x00, 0x20))
        assertEquals(ProtocolKind.NEED_MORE, classify(0x16))
        assertEquals(ProtocolKind.NEED_MORE, classify(0x16, 0x03))
        assertEquals(ProtocolKind.MINECRAFT, classify(0x16, 0x00, 0x01))
    }

    @Test
    fun minecraftLegacyAndHandshake() {
        assertEquals(ProtocolKind.MINECRAFT, classify(0xFE, 0x01))
        assertEquals(ProtocolKind.MINECRAFT, classify(0x10, 0x00, 0xFB, 0x05))
        assertEquals(ProtocolKind.MINECRAFT, classify(0x00))
    }

    private fun classify(text: String): ProtocolKind =
        classify(*text.toByteArray().map { it.toInt() and 0xFF }.toIntArray())

    private fun classify(vararg bytes: Int): ProtocolKind {
        val buf = Unpooled.wrappedBuffer(ByteArray(bytes.size) { bytes[it].toByte() })
        try {
            return ProtocolClassifier.classify(buf)
        } finally {
            buf.release()
        }
    }
}
