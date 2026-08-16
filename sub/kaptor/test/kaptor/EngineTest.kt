package kaptor

import kaptor.runtime.ScriptEventBus
import kaptor.runtime.ScriptManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var engine: ScriptEngine

    @BeforeEach
    fun setUp() {
        ScriptManager.reset()
        val config = ScriptEngineConfig(enableHotReload = false)
        engine = ScriptEngine(config)
    }

    @AfterEach
    fun tearDown() {
        engine.shutdown()
    }

    private fun script(body: String): String = body.trimIndent() + "\n"

    // ── init / empty ──────────────────────────────────────────────

    @Test
    fun `engine initializes with empty directory`() {
        engine.init(tempDir)
        assertEquals(0, engine.getStats().loadedScripts)
        assertTrue(engine.getLoadedScripts().isEmpty())
        assertTrue(engine.getStats().registeredEventTypes.isEmpty())
    }

    @Test
    fun `engine init is idempotent`() {
        engine.init(tempDir)
        engine.init(tempDir)
        assertEquals(0, engine.getStats().loadedScripts)
    }

    // ── basic script loading ──────────────────────────────────────

    @Test
    fun `engine loads script files`() {
        val scriptPath = tempDir.resolve("test.kts")
        Files.writeString(scriptPath, "val x = 42\n")
        assertTrue(Files.exists(scriptPath))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        assertTrue(engine.getLoadedScripts().contains("test"))
    }

    @Test
    fun `engine loads multiple script files`() {
        Files.writeString(tempDir.resolve("a.kts"), "val x = 1\n")
        Files.writeString(tempDir.resolve("b.kts"), "val y = 2\n")
        engine.init(tempDir)
        assertEquals(2, engine.getStats().loadedScripts)
        assertTrue(engine.getLoadedScripts().containsAll(listOf("a", "b")))
    }

    @Test
    fun `engine handles parse error gracefully`() {
        Files.writeString(tempDir.resolve("bad.kts"), "class class class\n")
        engine.init(tempDir)
        assertEquals(0, engine.getStats().loadedScripts)
    }

    // ── reload / shutdown ─────────────────────────────────────────

    @Test
    fun `engine reload clears and reloads`() {
        val scriptPath = tempDir.resolve("test.kts")
        Files.writeString(scriptPath, "val x = 42\n")
        assertTrue(Files.exists(scriptPath))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        engine.reloadAll()
        assertEquals(1, engine.getStats().loadedScripts)
    }

    @Test
    fun `engine shutdown clears all handlers`() {
        Files.writeString(
            tempDir.resolve("h.kts"), script(
                """
            on("TestEvent") { event -> val x = event.name }
        """
            )
        )
        engine.init(tempDir)
        assertTrue(ScriptEventBus.getRegisteredEventTypes().isNotEmpty())
        engine.shutdown()
        assertTrue(ScriptEventBus.getRegisteredEventTypes().isEmpty())
    }

    @Test
    fun `engine shutdown clears loaded scripts from bus`() {
        Files.writeString(tempDir.resolve("a.kts"), "val x = 1\n")
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        assertTrue(ScriptEventBus.getRegisteredEventTypes().isEmpty())
        engine.shutdown()
        assertEquals(0, ScriptEventBus.getHandlerCount())
    }

    // ── stats ─────────────────────────────────────────────────────

    @Test
    fun `engine stats track handlers only when events are present`() {
        engine.init(tempDir)
        assertEquals(0, engine.getStats().totalHandlers)
    }

    @Test
    fun `stats reflect loaded handlers`() {
        Files.writeString(
            tempDir.resolve("h.kts"), script(
                """
            on("A") { event -> val x = event.x }
            before("B") { event -> val y = event.y }
            after("C") { event -> val z = event.z }
        """
            )
        )
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        assertEquals(3, engine.getStats().totalHandlers)
        assertEquals(setOf("A", "B", "C"), engine.getStats().registeredEventTypes)
    }

    @Test
    fun `stats after loading multiple scripts`() {
        Files.writeString(
            tempDir.resolve("a.kts"), script(
                """
            on("E1") { event -> val x = event.x }
        """
            )
        )
        Files.writeString(
            tempDir.resolve("b.kts"), script(
                """
            before("E2") { event -> val y = event.y }
        """
            )
        )
        engine.init(tempDir)
        assertEquals(2, engine.getStats().loadedScripts)
        assertEquals(2, engine.getStats().totalHandlers)
        assertTrue(engine.getStats().registeredEventTypes.containsAll(listOf("E1", "E2")))
    }

    // ── event dispatch – no handlers ──────────────────────────────

    @Test
    fun `engine dispatch does nothing with no handlers`() {
        engine.init(tempDir)
        ScriptEventBus.dispatchEvent("TestEvent", mapOf("key" to "value"))
        assertTrue(engine.getStats().registeredEventTypes.isEmpty())
    }

    @Test
    fun `dispatch to unregistered event does not throw`() {
        Files.writeString(
            tempDir.resolve("h.kts"), script(
                """
            on("A") { event -> val x = event.x }
        """
            )
        )
        engine.init(tempDir)
        assertDoesNotThrow {
            ScriptEventBus.dispatchEvent("NonExistent", emptyMap<String, Any>())
        }
    }

    // ── unload / reload scripts ───────────────────────────────────

    @Test
    fun `unload script removes handlers`() {
        Files.writeString(
            tempDir.resolve("h.kts"), script(
                """
            on("E") { event -> }
        """
            )
        )
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertTrue(ScriptManager.unloadScript("h"))
        assertEquals(0, engine.getStats().totalHandlers)
        assertTrue(engine.getStats().registeredEventTypes.isEmpty())
    }

    @Test
    fun `unload non-existent script returns false`() {
        engine.init(tempDir)
        assertFalse(ScriptManager.unloadScript("nonexistent"))
    }

    @Test
    fun `reload after file change`() {
        val path = tempDir.resolve("h.kts")
        Files.writeString(
            path, script(
                """
            on("E1") { event -> }
        """
            )
        )
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertEquals(setOf("E1"), engine.getStats().registeredEventTypes)

        Files.writeString(
            path, script(
                """
            on("E2") { event -> }
        """
            )
        )
        assertTrue(ScriptManager.reloadScript(path))
        assertEquals(1, engine.getStats().totalHandlers)
        assertEquals(setOf("E2"), engine.getStats().registeredEventTypes)
    }

    // ── language service ──────────────────────────────────────────

    @Test
    fun `engine language service is accessible`() {
        engine.init(tempDir)
        val service = engine.getLanguageService()
        assertNull(service.getCachedResult("nonexistent"))
    }
}
