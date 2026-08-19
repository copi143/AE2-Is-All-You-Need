package allyouneed.net.http

import com.google.gson.Gson
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.util.CharsetUtil
import net.minecraft.server.MinecraftServer

class HttpCall(
    private val ctx: ChannelHandlerContext,
    request: FullHttpRequest,
    val server: MinecraftServer?,
    val params: Map<String, String> = emptyMap(),
) {
    val method: HttpMethod = request.method()
    val path: String
    val query: Map<String, List<String>>
    val body: ByteArray
    private val keepAlive: Boolean

    init {
        val decoder = QueryStringDecoder(request.uri())
        path = normalizePath(decoder.path())
        query = decoder.parameters()
        val content = request.content()
        body = ByteArray(content.readableBytes())
        content.getBytes(content.readerIndex(), body)
        keepAlive = HttpUtil.isKeepAlive(request)
    }

    fun json(payload: Any?, status: HttpResponseStatus = HttpResponseStatus.OK) {
        val bytes = GSON.toJson(payload).toByteArray(Charsets.UTF_8)
        reply(status, "application/json; charset=UTF-8", Unpooled.wrappedBuffer(bytes))
    }

    fun text(
        payload: String,
        status: HttpResponseStatus = HttpResponseStatus.OK,
        contentType: String = "text/plain; charset=UTF-8",
    ) {
        reply(status, contentType, Unpooled.copiedBuffer(payload, CharsetUtil.UTF_8))
    }

    fun bytes(
        payload: ByteArray,
        contentType: String,
        status: HttpResponseStatus = HttpResponseStatus.OK,
    ) {
        reply(status, contentType, Unpooled.wrappedBuffer(payload))
    }

    fun onServer(block: (MinecraftServer) -> Any?) {
        val running = server
        if (running == null || running.isStopped) {
            json(mapOf("error" to "server unavailable"), HttpResponseStatus.SERVICE_UNAVAILABLE)
            return
        }
        if (running.isSameThread) {
            json(runCatching { block(running) }.getOrElse { mapOf("error" to (it.message ?: "error")) })
            return
        }
        running.execute {
            val result = runCatching { block(running) }
            ctx.channel().eventLoop().execute {
                if (!ctx.channel().isActive) return@execute
                result.fold(
                    onSuccess = { json(it) },
                    onFailure = {
                        json(mapOf("error" to (it.message ?: "error")), HttpResponseStatus.INTERNAL_SERVER_ERROR)
                    },
                )
            }
        }
    }

    private fun reply(status: HttpResponseStatus, contentType: String, payload: ByteBuf) {
        val res = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, payload)
        res.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, payload.readableBytes())
        if (keepAlive) {
            res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            ctx.writeAndFlush(res)
        } else {
            res.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
        }
    }

    companion object {
        internal val GSON = Gson()

        fun normalizePath(path: String): String =
            if (path.length > 1 && path.endsWith('/')) path.dropLast(1) else path
    }
}
