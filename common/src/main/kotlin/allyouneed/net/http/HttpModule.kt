package allyouneed.net.http

object HttpModule {
    const val WEB_ROOT = "assets/ae2isallyouneed/web"

    fun register() {
        HttpApi.get("/api") { call ->
            call.json(
                mapOf(
                    "ok" to true,
                    "api" to HttpApi.list().map { "${it.method.name()} ${it.path}" },
                    "pages" to HttpPages.list().map { "${it.kind} ${it.url} -> ${it.source}" },
                ),
            )
        }
        HttpApi.get("/api/stats") { call ->
            call.onServer { GameStats.snapshot(it) }
        }
        HttpPages.mount("/", WEB_ROOT)
    }
}
