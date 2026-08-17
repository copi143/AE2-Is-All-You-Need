package allyouneed.net.http

import io.netty.handler.codec.http.HttpMethod
import java.util.concurrent.CopyOnWriteArrayList

fun interface HttpHandler {
    fun handle(call: HttpCall)
}

class ApiMatch(
    val handler: HttpHandler,
    val params: Map<String, String>,
    val pattern: String,
)

class ApiEntry(
    val method: HttpMethod,
    val path: String,
)

object HttpApi {
    private sealed class Segment {
        class Lit(val value: String) : Segment()
        class Param(val name: String) : Segment()
    }

    private class Route(
        val method: HttpMethod,
        val path: String,
        val segments: List<Segment>,
        val handler: HttpHandler,
    ) {
        val literals: Int = segments.count { it is Segment.Lit }
    }

    private val routes = CopyOnWriteArrayList<Route>()

    fun get(path: String, handler: HttpHandler) = add(HttpMethod.GET, path, handler)
    fun post(path: String, handler: HttpHandler) = add(HttpMethod.POST, path, handler)
    fun put(path: String, handler: HttpHandler) = add(HttpMethod.PUT, path, handler)
    fun delete(path: String, handler: HttpHandler) = add(HttpMethod.DELETE, path, handler)
    fun patch(path: String, handler: HttpHandler) = add(HttpMethod.PATCH, path, handler)
    fun head(path: String, handler: HttpHandler) = add(HttpMethod.HEAD, path, handler)
    fun options(path: String, handler: HttpHandler) = add(HttpMethod.OPTIONS, path, handler)

    fun add(method: HttpMethod, path: String, handler: HttpHandler) {
        val normalized = HttpCall.normalizePath(path)
        val route = Route(method, normalized, parse(normalized), handler)
        routes.removeIf { it.method == method && it.path == normalized }
        routes += route
    }

    fun match(method: HttpMethod, path: String): ApiMatch? {
        val segs = split(HttpCall.normalizePath(path))
        var best: Route? = null
        var bestParams: Map<String, String> = emptyMap()
        for (route in routes) {
            if (route.method != method) continue
            if (route.segments.size != segs.size) continue
            val params = HashMap<String, String>()
            var ok = true
            for (i in route.segments.indices) {
                when (val seg = route.segments[i]) {
                    is Segment.Lit -> if (seg.value != segs[i]) {
                        ok = false
                        break
                    }
                    is Segment.Param -> params[seg.name] = segs[i]
                }
            }
            if (!ok) continue
            if (best == null || route.literals > best.literals) {
                best = route
                bestParams = params
            }
        }
        return best?.let { ApiMatch(it.handler, bestParams, it.path) }
    }

    fun list(): List<ApiEntry> = routes.map { ApiEntry(it.method, it.path) }

    internal fun clear() {
        routes.clear()
    }

    private fun parse(path: String): List<Segment> = split(path).map { part ->
        if (part.length > 2 && part.startsWith("{") && part.endsWith("}")) {
            Segment.Param(part.substring(1, part.lastIndex))
        } else {
            Segment.Lit(part)
        }
    }

    private fun split(path: String): List<String> =
        if (path == "/") emptyList() else path.trimStart('/').split('/')
}
