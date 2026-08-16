package allyouneed.util.native

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNotifyTest {

    @AfterEach
    fun tearDown() {
        DesktopNotify.resetForTest()
    }

    @Test
    fun `sanitize strips NUL and truncates`() {
        assertEquals("ab", sanitizeNotifyText("a\u0000b", 10))
        assertEquals("abc", sanitizeNotifyText("  abc  ", 10))
        assertEquals("ab", sanitizeNotifyText("abcd", 2))
        assertEquals("", sanitizeNotifyText("", 10))
        assertEquals("", sanitizeNotifyText("\u0000\u0000", 10))
    }

    @Test
    fun `apple script escapes quotes and backslashes`() {
        assertEquals("""hello \"world\"""", escapeAppleScript("""hello "world""""))
        assertEquals("""a\\b""", escapeAppleScript("""a\b"""))
    }

    @Test
    fun `uninitialized focus probe does not send`() {
        val sent = ArrayList<Pair<String, String>>()
        DesktopNotify.backend = recordingBackend(sent)
        DesktopNotify.enqueue = { it.run() }
        DesktopNotify.focusProbe = null
        DesktopNotify.send("t", "b")
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `focused window does not send`() {
        val sent = ArrayList<Pair<String, String>>()
        DesktopNotify.backend = recordingBackend(sent)
        DesktopNotify.enqueue = { it.run() }
        DesktopNotify.focusProbe = DesktopNotify.FocusProbe { true }
        DesktopNotify.send("t", "b")
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `unfocused window sends sanitized text`() {
        val sent = ArrayList<Pair<String, String>>()
        DesktopNotify.backend = recordingBackend(sent)
        DesktopNotify.enqueue = { it.run() }
        DesktopNotify.focusProbe = DesktopNotify.FocusProbe { false }
        DesktopNotify.send("  hi\u0000 ", "body")
        assertEquals(listOf("hi" to "body"), sent)
    }

    @Test
    fun `linux falls back when first tool is missing`() {
        val ran = ArrayList<String>()
        val backend = OsNotifyBackend(
            os = OsKind.Linux,
            resolve = { name -> name.takeIf { it == "gdbus" }?.let { "/usr/bin/$it" } },
            runner = { cmd ->
                ran += cmd.argv.first()
                cmd.argv.first().endsWith("gdbus")
            },
        )
        assertTrue(backend.send("title", "body"))
        assertEquals(listOf("/usr/bin/gdbus"), ran)
    }

    @Test
    fun `all missing tools become unavailable`() {
        val backend = OsNotifyBackend(
            os = OsKind.Linux,
            resolve = { null },
            runner = { error("should not run") },
        )
        assertFalse(backend.send("t", "b"))
        assertTrue(backend.isUnavailable)
        assertFalse(backend.send("t", "b"))
    }

    @Test
    fun `failed cached tool tries remaining then caches success`() {
        val ran = ArrayList<String>()
        val backend = OsNotifyBackend(
            os = OsKind.Linux,
            resolve = { name -> "/bin/$name".takeIf { name == "notify-send" || name == "gdbus" } },
            runner = { cmd ->
                val name = cmd.argv.first().substringAfterLast('/')
                ran += name
                name == "gdbus"
            },
        )
        assertTrue(backend.send("t", "b"))
        assertEquals(listOf("notify-send", "gdbus"), ran)
        ran.clear()
        assertTrue(backend.send("t", "b"))
        assertEquals(listOf("gdbus"), ran)
    }

    @Test
    fun `notify-send argv does not use a shell`() {
        val tools = linuxTools { "/usr/bin/$it" }
        val cmd = tools.first { it.name == "notify-send" }.build("/usr/bin/notify-send", "--evil", "x; rm -rf /")
        assertEquals(
            listOf("/usr/bin/notify-send", "--app-name=AE2 Is All You Need", "--", "--evil", "x; rm -rf /"),
            cmd.argv,
        )
    }

    @Test
    fun `gdbus argv keeps title and body as separate arguments`() {
        val tools = linuxTools { "/usr/bin/$it" }
        val cmd = tools.first { it.name == "gdbus" }.build("/usr/bin/gdbus", "T", "B")
        assertEquals("T", cmd.argv[cmd.argv.indexOf("5000") - 4])
        assertEquals("B", cmd.argv[cmd.argv.indexOf("5000") - 3])
    }

    @Test
    fun `native success skips process tools`() {
        var processRan = false
        val backend = OsNotifyBackend(
            os = OsKind.Linux,
            resolve = { "/bin/true" },
            runner = { processRan = true; true },
            nativeSend = { _, _ -> true },
        )
        assertTrue(backend.send("t", "b"))
        assertFalse(processRan)
    }

    @Test
    fun `native failure falls through to process tools`() {
        val ran = ArrayList<String>()
        val backend = OsNotifyBackend(
            os = OsKind.Linux,
            resolve = { name -> name.takeIf { it == "gdbus" }?.let { "/usr/bin/$it" } },
            runner = { cmd ->
                ran += cmd.argv.first()
                true
            },
            nativeSend = { _, _ -> false },
        )
        assertTrue(backend.send("t", "b"))
        assertEquals(listOf("/usr/bin/gdbus"), ran)
        ran.clear()
        assertTrue(backend.send("t", "b"))
        assertEquals(listOf("/usr/bin/gdbus"), ran)
    }

    @Test
    fun `windows toast passes text via env not argv interpolation`() {
        val tools = windowsTools { "powershell.exe" }
        val cmd = tools.first { it.name == "powershell-toast" }.build("powershell.exe", "ti\$tle", "bo\"dy")
        assertEquals("ti\$tle", cmd.env["AYN_NOTIFY_TITLE"])
        assertEquals("bo\"dy", cmd.env["AYN_NOTIFY_BODY"])
        assertFalse(cmd.argv.any { it.contains("ti\$tle") || it.contains("bo\"dy") })
    }
}

class DesktopNotifyLiveTest {

    @AfterEach
    fun tearDown() {
        DesktopNotify.resetForTest()
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AYN_NOTIFY_LIVE", matches = "1")
    fun `live native send on this desktop`() {
        NativeNotify.resetForTest()
        val ok = tryNativeNotify("AE2 Is All You Need", "JVM 直接调用 libnotify")
        assertTrue(ok, NativeNotify.lastError?.stackTraceToString() ?: "native notify returned false")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AYN_NOTIFY_LIVE", matches = "1")
    fun `live send on this desktop`() {
        DesktopNotify.backend = defaultNotifyBackend()
        assertTrue(DesktopNotify.sendBlocking("AE2 Is All You Need", "桌面通知测试"))
    }
}

private fun recordingBackend(sink: MutableList<Pair<String, String>>) = OsNotifyBackend(
    os = OsKind.Linux,
    resolve = { "/bin/true" },
    runner = { cmd ->
        val title = cmd.argv.getOrNull(cmd.argv.size - 2) ?: ""
        val body = cmd.argv.last()
        sink += title to body
        true
    },
)
