package allyouneed.net.http

import allyouneed.util.debugLogger
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import net.minecraft.network.Connection
import net.minecraft.server.network.ServerConnectionListener

class ProtocolDetector(
    private val listener: ServerConnectionListener,
) : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        when (ProtocolClassifier.classify(input)) {
            ProtocolKind.NEED_MORE -> return
            ProtocolKind.MINECRAFT -> ctx.pipeline().remove(this)
            ProtocolKind.HTTP -> adoptHttp(ctx, tls = false)
            ProtocolKind.TLS -> adoptHttp(ctx, tls = true)
        }
    }

    private fun adoptHttp(ctx: ChannelHandlerContext, tls: Boolean) {
        val pipeline = ctx.pipeline()
        val connection = pipeline.get("packet_handler") as? Connection
        if (connection != null) {
            val connections = listener.getConnections()
            synchronized(connections) {
                connections.remove(connection)
            }
        }
        for (name in SharedHttp.MC_HANDLERS) {
            if (pipeline.get(name) != null) pipeline.remove(name)
        }
        if (tls) {
            pipeline.addLast(SharedHttp.SSL, SharedHttp.sslContext.newHandler(ctx.alloc()))
        }
        pipeline.addLast(SharedHttp.HTTP_CODEC, HttpServerCodec())
        pipeline.addLast(SharedHttp.HTTP_AGG, HttpObjectAggregator(65536))
        pipeline.addLast(SharedHttp.HTTP_ROUTER, HttpRouter(listener.server))
        debugLogger.debug("adopted HTTP{} from {}", if (tls) "S" else "", ctx.channel().remoteAddress())
        pipeline.remove(this)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        ctx.close()
    }
}
