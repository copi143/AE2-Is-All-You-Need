package allyouneed.net.http

import io.netty.handler.codec.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpRouterTest {
    @BeforeTest
    fun setUp() {
        HttpApi.clear()
        HttpPages.clear()
        HttpModule.register()
    }

    @AfterTest
    fun tearDown() {
        HttpApi.clear()
        HttpPages.clear()
    }

    @Test
    fun defaultApiRoutes() {
        assertNotNull(HttpApi.match(HttpMethod.GET, "/api"))
        assertNotNull(HttpApi.match(HttpMethod.GET, "/api/stats"))
        assertNotNull(HttpApi.match(HttpMethod.GET, "/api/stats/"))
        assertNull(HttpApi.match(HttpMethod.POST, "/api/stats"))
        assertNull(HttpApi.match(HttpMethod.GET, "/nope"))
        assertNull(HttpApi.match(HttpMethod.GET, "/"))
    }

    @Test
    fun laterRegisterReplacesSameRoute() {
        HttpApi.get("/api/stats") { }
        assertNotNull(HttpApi.match(HttpMethod.GET, "/api/stats"))
        assertEquals(1, HttpApi.list().count { it.path == "/api/stats" })
    }

    @Test
    fun pathParamsPreferLiterals() {
        HttpApi.get("/api/grids/{mac}") { }
        HttpApi.get("/api/grids/all") { }
        val all = HttpApi.match(HttpMethod.GET, "/api/grids/all")
        val one = HttpApi.match(HttpMethod.GET, "/api/grids/aa-bb")
        assertEquals("/api/grids/all", all?.pattern)
        assertEquals("/api/grids/{mac}", one?.pattern)
        assertEquals("aa-bb", one?.params?.get("mac"))
        assertTrue(all?.params.isNullOrEmpty())
    }

    @Test
    fun normalizePathDropsTrailingSlash() {
        assertEquals("/", HttpCall.normalizePath("/"))
        assertEquals("/api/stats", HttpCall.normalizePath("/api/stats/"))
        assertEquals("/api/stats", HttpCall.normalizePath("/api/stats"))
    }
}
