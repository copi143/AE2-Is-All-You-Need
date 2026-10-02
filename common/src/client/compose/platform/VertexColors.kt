package allyouneed.client.compose.platform

import java.nio.ByteBuffer

/** GL_UNSIGNED_BYTE color attributes consume RGBA bytes, regardless of the buffer's byte order. */
internal fun ByteBuffer.putRgba(argb: Int) {
    put((argb ushr 16).toByte())
    put((argb ushr 8).toByte())
    put(argb.toByte())
    put((argb ushr 24).toByte())
}
