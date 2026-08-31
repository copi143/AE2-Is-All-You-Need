package allyouneed.net.http

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpPagesTest {
    @BeforeTest
    fun setUp() {
        HttpPages.clear()
        HttpPages.opener = ClasspathOpen { path ->
            when (path) {
                "assets/ae2isallyouneed/web/index.html" -> "<html>root</html>".toByteArray()
                "assets/ae2isallyouneed/web/app.js" -> "console.log(1)".toByteArray()
                "assets/other/status.html" -> "<p>ok</p>".toByteArray()
                else -> null
            }
        }
        HttpPages.mount("/", "assets/ae2isallyouneed/web")
        HttpPages.page("/status", "assets/other/status.html")
        HttpPages.bytes("/ping", "text/plain; charset=UTF-8", "pong".toByteArray())
    }

    @AfterTest
    fun tearDown() {
        HttpPages.clear()
    }

    @Test
    fun mountServesIndexAndFiles() {
        val index = HttpPages.match("/")
        assertNotNull(index)
        assertEquals("text/html; charset=UTF-8", index.contentType)
        assertEquals("<html>root</html>", index.bytes.decodeToString())
        val js = HttpPages.match("/app.js")
        assertNotNull(js)
        assertEquals("text/javascript; charset=UTF-8", js.contentType)
    }

    @Test
    fun exactPageAndBytesWin() {
        assertEquals("<p>ok</p>", HttpPages.match("/status")?.bytes?.decodeToString())
        assertEquals("pong", HttpPages.match("/ping")?.bytes?.decodeToString())
    }

    @Test
    fun rejectsTraversal() {
        assertNull(HttpPages.safeJoin("assets/web", "../secret"))
        assertNull(HttpPages.safeJoin("assets/web", "a/../../x"))
        assertNull(HttpPages.match("/../secret"))
        assertEquals("assets/web/app.js", HttpPages.safeJoin("assets/web", "app.js"))
    }

    @Test
    fun catalogListsRegistrations() {
        val listed = HttpPages.list().map { "${it.kind}:${it.url}" }
        assertTrue(listed.contains("mount:/"))
        assertTrue(listed.contains("page:/status"))
        assertTrue(listed.contains("bytes:/ping"))
    }
}
