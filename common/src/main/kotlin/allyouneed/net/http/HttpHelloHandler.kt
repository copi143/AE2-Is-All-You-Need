package allyouneed.net.http

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.util.CharsetUtil

class HttpHelloHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        if (!msg.decoderResult().isSuccess) {
            ctx.close()
            return
        }
        val path = QueryStringDecoder(msg.uri()).path()
        val ok = msg.method() == HttpMethod.GET && (path == "/" || path.isEmpty())
        val body = Unpooled.copiedBuffer(if (ok) "hello" else "not found", CharsetUtil.UTF_8)
        val res = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            if (ok) HttpResponseStatus.OK else HttpResponseStatus.NOT_FOUND,
            body,
        )
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())
        res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }
}
