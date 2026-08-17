package allyouneed.net.http

import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets

enum class ProtocolKind {
    NEED_MORE,
    MINECRAFT,
    HTTP,
    TLS,
}

object ProtocolClassifier {
    private const val TLS_HANDSHAKE = 0x16
    private const val TLS_MAJOR = 0x03

    private val HTTP_PREFIXES = listOf(
        "GET ",
        "POST ",
        "HEAD ",
        "PUT ",
        "PATCH ",
        "DELETE ",
        "OPTIONS ",
        "CONNECT ",
        "TRACE ",
        "PRI * HTTP",
    ).map { it.toByteArray(StandardCharsets.US_ASCII) }

    @JvmStatic
    fun classify(buf: ByteBuf): ProtocolKind {
        val n = buf.readableBytes()
        if (n < 1) return ProtocolKind.NEED_MORE
        val i = buf.readerIndex()
        val b0 = buf.getUnsignedByte(i).toInt()
        if (b0 == TLS_HANDSHAKE) {
            if (n < 3) return ProtocolKind.NEED_MORE
            val major = buf.getUnsignedByte(i + 1).toInt()
            val minor = buf.getUnsignedByte(i + 2).toInt()
            return if (major == TLS_MAJOR && minor <= 0x04) ProtocolKind.TLS else ProtocolKind.MINECRAFT
        }
        if (b0 == 0xFE) return ProtocolKind.MINECRAFT
        return classifyHttp(buf, i, n)
    }

    private fun classifyHttp(buf: ByteBuf, index: Int, readable: Int): ProtocolKind {
        var waiting = false
        for (prefix in HTTP_PREFIXES) {
            val cmp = minOf(readable, prefix.size)
            var match = true
            for (k in 0 until cmp) {
                if (buf.getUnsignedByte(index + k).toInt() != (prefix[k].toInt() and 0xFF)) {
                    match = false
                    break
                }
            }
            if (!match) continue
            if (readable >= prefix.size) return ProtocolKind.HTTP
            waiting = true
        }
        return if (waiting) ProtocolKind.NEED_MORE else ProtocolKind.MINECRAFT
    }
}
