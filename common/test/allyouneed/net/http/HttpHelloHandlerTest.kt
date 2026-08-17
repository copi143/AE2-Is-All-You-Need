package allyouneed.net.http

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.util.CharsetUtil
import io.netty.util.ReferenceCountUtil
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class HttpHelloHandlerTest {
    @Test
    fun getRootReturnsHello() {
        val text = exchange("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n")
        assertContains(text, "HTTP/1.1 200")
        assertContains(text, "hello")
    }

    @Test
    fun otherPathIsNotFound() {
        val text = exchange("GET /nope HTTP/1.1\r\nHost: localhost\r\n\r\n")
        assertContains(text, "HTTP/1.1 404")
    }

    private fun exchange(request: String): String {
        val ch = EmbeddedChannel(
            HttpServerCodec(),
            HttpObjectAggregator(65536),
            HttpHelloHandler(),
        )
        ch.writeInbound(Unpooled.copiedBuffer(request, CharsetUtil.US_ASCII))
        val buf = Unpooled.buffer()
        while (true) {
            val out = ch.readOutbound<Any>() ?: break
            if (out is ByteBuf) buf.writeBytes(out)
            ReferenceCountUtil.release(out)
        }
        val text = buf.toString(CharsetUtil.UTF_8)
        buf.release()
        ch.finishAndReleaseAll()
        assertTrue(text.isNotEmpty())
        return text
    }
}
