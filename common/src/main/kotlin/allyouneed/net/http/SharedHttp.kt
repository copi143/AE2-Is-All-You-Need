package allyouneed.net.http

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate

object SharedHttp {
    const val PROP = "ae2isallyouneed.http"
    const val DETECTOR = "allyouneed_proto"
    const val SSL = "ssl"
    const val HTTP_CODEC = "http_codec"
    const val HTTP_AGG = "http_agg"
    const val HTTP_ROUTER = "http_router"

    val MC_HANDLERS = listOf(
        "legacy_query",
        "splitter",
        "decoder",
        "prepender",
        "encoder",
        "packet_handler",
    )

    @JvmStatic
    fun enabled(): Boolean = System.getProperty(PROP, "true") != "false"

    val sslContext: SslContext by lazy {
        val cert = SelfSignedCertificate()
        SslContextBuilder.forServer(cert.certificate(), cert.privateKey()).build()
    }
}
