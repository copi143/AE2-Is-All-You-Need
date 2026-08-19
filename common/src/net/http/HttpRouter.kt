package allyouneed.net.http

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import net.minecraft.server.MinecraftServer

class HttpRouter(
    private val server: MinecraftServer?,
) : SimpleChannelInboundHandler<FullHttpRequest>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {
        if (!msg.decoderResult().isSuccess) {
            ctx.close()
            return
        }
        val path = HttpCall.normalizePath(QueryStringDecoder(msg.uri()).path())
        val api = HttpApi.match(msg.method(), path)
        if (api != null) {
            val call = HttpCall(ctx, msg, server, api.params)
            try {
                api.handler.handle(call)
            } catch (_: Exception) {
                call.text("error", HttpResponseStatus.INTERNAL_SERVER_ERROR)
            }
            return
        }
        if (msg.method() == HttpMethod.GET) {
            val page = HttpPages.match(path)
            if (page != null) {
                HttpCall(ctx, msg, server).bytes(page.bytes, page.contentType)
                return
            }
        }
        HttpCall(ctx, msg, server).text("not found", HttpResponseStatus.NOT_FOUND)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }
}
