package kaptor

import kaptor.ast.HookType
import kaptor.runtime.ScriptEventBus
import kaptor.runtime.ScriptManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertDoesNotThrow

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
        val scriptPath = tempDir.resolve("test.script")
        Files.writeString(scriptPath, "val x = 42\n")
        assertTrue(Files.exists(scriptPath))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        assertTrue(engine.getLoadedScripts().contains("test"))
    }

    @Test
    fun `engine loads multiple script files`() {
        Files.writeString(tempDir.resolve("a.script"), "val x = 1\n")
        Files.writeString(tempDir.resolve("b.script"), "val y = 2\n")
        engine.init(tempDir)
        assertEquals(2, engine.getStats().loadedScripts)
        assertTrue(engine.getLoadedScripts().containsAll(listOf("a", "b")))
    }

    @Test
    fun `engine handles parse error gracefully`() {
        Files.writeString(tempDir.resolve("bad.script"), "class class class\n")
        engine.init(tempDir)
        assertEquals(0, engine.getStats().loadedScripts)
    }

    // ── reload / shutdown ─────────────────────────────────────────

    @Test
    fun `engine reload clears and reloads`() {
        val scriptPath = tempDir.resolve("test.script")
        Files.writeString(scriptPath, "val x = 42\n")
        assertTrue(Files.exists(scriptPath))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        engine.reloadAll()
        assertEquals(1, engine.getStats().loadedScripts)
    }

    @Test
    fun `engine shutdown clears all handlers`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("TestEvent") { event -> val x = event.name }
        """))
        engine.init(tempDir)
        assertTrue(ScriptEventBus.getRegisteredEventTypes().isNotEmpty())
        engine.shutdown()
        assertTrue(ScriptEventBus.getRegisteredEventTypes().isEmpty())
    }

    @Test
    fun `engine shutdown clears loaded scripts from bus`() {
        Files.writeString(tempDir.resolve("a.script"), "val x = 1\n")
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
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("A") { event -> val x = event.x }
            before("B") { event -> val y = event.y }
            after("C") { event -> val z = event.z }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        assertEquals(3, engine.getStats().totalHandlers)
        assertEquals(setOf("A", "B", "C"), engine.getStats().registeredEventTypes)
    }

    @Test
    fun `stats after loading multiple scripts`() {
        Files.writeString(tempDir.resolve("a.script"), script("""
            on("E1") { event -> val x = event.x }
        """))
        Files.writeString(tempDir.resolve("b.script"), script("""
            before("E2") { event -> val y = event.y }
        """))
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
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("A") { event -> val x = event.x }
        """))
        engine.init(tempDir)
        assertDoesNotThrow {
            ScriptEventBus.dispatchEvent("NonExistent", emptyMap<String, Any>())
        }
    }

    // ── ON handler ────────────────────────────────────────────────

    @Test
    fun `engine load script with on handler`() {
        Files.writeString(tempDir.resolve("handler.script"), script("""
            on("TestEvent") { event ->
                val name = event.name
            }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        assertTrue(engine.getLoadedScripts().contains("handler"))
        assertTrue(ScriptEventBus.getRegisteredEventTypes().contains("TestEvent"))
        assertEquals(
            HookType.ON,
            ScriptEventBus.getHandlersForType("TestEvent").first().hookType
        )
    }

    @Test
    fun `on handler body is invoked without error`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("TestEvent") { event ->
                val name = event.name
            }
        """))
        engine.init(tempDir)
        val entry = ScriptEventBus.getHandlersForType("TestEvent").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("TestEvent", mapOf("name" to "test"))
        }
    }

    @Test
    fun `on handler with empty body`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("TestEvent") {}
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertEquals(setOf("TestEvent"), engine.getStats().registeredEventTypes)
        val entry = ScriptEventBus.getHandlersForType("TestEvent").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("TestEvent", emptyMap<String, Any>())
        }
    }

    @Test
    fun `on handler with val declaration in body`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("E") { event ->
                val x = 42
                val y = x
            }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        val entry = ScriptEventBus.getHandlersForType("E").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("E", emptyMap<String, Any>())
        }
    }

    @Test
    fun `on handler with var declaration and assignment`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("E") { event ->
                var x = 1
                x = 42
            }
        """))
        engine.init(tempDir)
        val entry = ScriptEventBus.getHandlersForType("E").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("E", emptyMap<String, Any>())
        }
    }

    @Test
    fun `on handler with if statement`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("E") { event ->
                if (true) {
                    val x = 1
                }
            }
        """))
        engine.init(tempDir)
        val entry = ScriptEventBus.getHandlersForType("E").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("E", emptyMap<String, Any>())
        }
    }

    @Test
    fun `on handler with return`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("E") { event ->
                return
            }
        """))
        engine.init(tempDir)
        val entry = ScriptEventBus.getHandlersForType("E").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("E", emptyMap<String, Any>())
        }
    }

    // ── BEFORE hook ───────────────────────────────────────────────

    @Test
    fun `loads script with before hook`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            before("TestEvent") { event ->
                val name = event.name
            }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertTrue(engine.getStats().registeredEventTypes.contains("TestEvent"))
        val entries = ScriptEventBus.getHandlersForType("TestEvent")
        assertEquals(1, entries.size)
        assertEquals(HookType.BEFORE, entries.first().hookType)
    }

    @Test
    fun `before handler body is invoked without error`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            before("E") { event ->
                val x = event.x
            }
        """))
        engine.init(tempDir)
        val entry = ScriptEventBus.getHandlersForType("E").first()
        assertEquals(HookType.BEFORE, entry.hookType)
        assertDoesNotThrow {
            entry.handler.invokeHandler("E", mapOf("x" to 1))
        }
    }

    @Test
    fun `multiple before handlers for same event`() {
        Files.writeString(tempDir.resolve("a.script"), script("""
            before("E") { event -> val x = event.x }
        """))
        Files.writeString(tempDir.resolve("b.script"), script("""
            before("E") { event -> val y = event.y }
        """))
        engine.init(tempDir)
        assertEquals(2, engine.getStats().totalHandlers)
        assertEquals(2, ScriptEventBus.getHandlersForType("E").size)
        assertTrue(ScriptEventBus.getHandlersForType("E").all { it.hookType == HookType.BEFORE })
    }

    // ── AFTER hook ────────────────────────────────────────────────

    @Test
    fun `loads script with after hook`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            after("TestEvent") { event ->
                val name = event.name
            }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertTrue(engine.getStats().registeredEventTypes.contains("TestEvent"))
        val entries = ScriptEventBus.getHandlersForType("TestEvent")
        assertEquals(1, entries.size)
        assertEquals(HookType.AFTER, entries.first().hookType)
    }

    @Test
    fun `after handler body is invoked without error`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            after("E") { event ->
                val x = event.x
            }
        """))
        engine.init(tempDir)
        val entry = ScriptEventBus.getHandlersForType("E").first()
        assertEquals(HookType.AFTER, entry.hookType)
        assertDoesNotThrow {
            entry.handler.invokeHandler("E", mapOf("x" to 1))
        }
    }

    @Test
    fun `multiple after handlers for same event`() {
        Files.writeString(tempDir.resolve("a.script"), script("""
            after("E") { event -> val x = event.x }
        """))
        Files.writeString(tempDir.resolve("b.script"), script("""
            after("E") { event -> val y = event.y }
        """))
        engine.init(tempDir)
        assertEquals(2, engine.getStats().totalHandlers)
        assertEquals(2, ScriptEventBus.getHandlersForType("E").size)
        assertTrue(ScriptEventBus.getHandlersForType("E").all { it.hookType == HookType.AFTER })
    }

    // ── combined hooks ────────────────────────────────────────────

    @Test
    fun `before on after hooks for same event in one script`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            before("E") { event -> val x = event.x }
            on("E") { event -> val y = event.y }
            after("E") { event -> val z = event.z }
        """))
        engine.init(tempDir)
        assertEquals(3, engine.getStats().totalHandlers)
        assertEquals(setOf("E"), engine.getStats().registeredEventTypes)
        val entries = ScriptEventBus.getHandlersForType("E")
        assertEquals(3, entries.size)
        val hooks = entries.map { it.hookType }
        assertTrue(hooks.contains(HookType.BEFORE))
        assertTrue(hooks.contains(HookType.ON))
        assertTrue(hooks.contains(HookType.AFTER))
    }

    @Test
    fun `before on after dispatch does not throw`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            before("E") { event -> }
            on("E") { event -> }
            after("E") { event -> }
        """))
        engine.init(tempDir)
        assertDoesNotThrow {
            ScriptEventBus.dispatchEvent("E", emptyMap<String, Any>())
        }
    }

    @Test
    fun `multiple scripts with different hook types`() {
        Files.writeString(tempDir.resolve("a.script"), script("""
            before("E") { event -> }
        """))
        Files.writeString(tempDir.resolve("b.script"), script("""
            on("E") { event -> }
        """))
        Files.writeString(tempDir.resolve("c.script"), script("""
            after("E") { event -> }
        """))
        engine.init(tempDir)
        assertEquals(3, engine.getStats().loadedScripts)
        assertEquals(3, engine.getStats().totalHandlers)
        assertEquals(setOf("E"), engine.getStats().registeredEventTypes)
    }

    // ── multiple events ───────────────────────────────────────────

    @Test
    fun `handlers for different event types`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("A") { event -> val x = event.x }
            on("B") { event -> val y = event.y }
        """))
        engine.init(tempDir)
        assertEquals(2, engine.getStats().totalHandlers)
        assertEquals(setOf("A", "B"), engine.getStats().registeredEventTypes)
    }

    @Test
    fun `before and after for different events`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            before("A") { event -> val x = event.x }
            after("B") { event -> val y = event.y }
        """))
        engine.init(tempDir)
        assertEquals(2, engine.getStats().totalHandlers)
        assertEquals(setOf("A", "B"), engine.getStats().registeredEventTypes)
    }

    @Test
    fun `dispatch to different event types`() {
        Files.writeString(tempDir.resolve("a.script"), script("""
            on("A") { event -> val x = event.x }
        """))
        Files.writeString(tempDir.resolve("b.script"), script("""
            on("B") { event -> val y = event.y }
        """))
        engine.init(tempDir)
        assertDoesNotThrow {
            ScriptEventBus.dispatchEvent("A", mapOf("x" to 1))
            ScriptEventBus.dispatchEvent("B", mapOf("y" to 2))
        }
    }

    // ── script mixing declarations and handlers ───────────────────

    @Test
    fun `script with mixed declarations and handlers`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            val x = 1
            on("E") { event -> val y = event.y }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().loadedScripts)
        assertEquals(1, engine.getStats().totalHandlers)
        assertEquals(setOf("E"), engine.getStats().registeredEventTypes)
    }

    // ── event type with special characters ────────────────────────

    @Test
    fun `handler with special characters in event type`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("my-event!") { event -> val x = event.x }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertTrue(engine.getStats().registeredEventTypes.contains("my-event!"))
        val entry = ScriptEventBus.getHandlersForType("my-event!").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("my-event!", mapOf("x" to 1))
        }
    }

    // ── unload / reload scripts ───────────────────────────────────

    @Test
    fun `unload script removes handlers`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("E") { event -> }
        """))
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
        val path = tempDir.resolve("h.script")
        Files.writeString(path, script("""
            on("E1") { event -> }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertEquals(setOf("E1"), engine.getStats().registeredEventTypes)

        Files.writeString(path, script("""
            on("E2") { event -> }
        """))
        assertTrue(ScriptManager.reloadScript(path))
        assertEquals(1, engine.getStats().totalHandlers)
        assertEquals(setOf("E2"), engine.getStats().registeredEventTypes)
    }

    // ── language service ──────────────────────────────────────────

    @Test
    fun `engine language service is accessible`() {
        engine.init(tempDir)
        val service = engine.getLanguageService()
        assertTrue(service.getCachedResult("nonexistent") == null)
    }

    // ── typed event wrapper (declareEvent) ────────────────────────

    @Test
    fun `declare event with typed wrapper dispatches without error`() {
        engine.declareEvent("TestEvent") {
            string("name")
            int("count")
        }
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("TestEvent") { event ->
                val n = event.name
                val c = event.count
            }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertDoesNotThrow {
            engine.dispatchEvent("TestEvent", mapOf("name" to "hello", "count" to 42))
        }
    }

    @Test
    fun `typed wrapper with multiple event types`() {
        engine.declareEvent("A") { any("x") }
        engine.declareEvent("B") { any("y") }
        Files.writeString(tempDir.resolve("a.script"), script("""
            on("A") { event -> val x = event.x }
            on("B") { event -> val y = event.y }
        """))
        engine.init(tempDir)
        assertEquals(2, engine.getStats().totalHandlers)
        assertDoesNotThrow {
            engine.dispatchEvent("A", mapOf("x" to 1))
            engine.dispatchEvent("B", mapOf("y" to 2))
        }
    }

    @Test
    fun `declare event with mixed hooks dispatched through engine`() {
        engine.declareEvent("E") { string("msg") }
        Files.writeString(tempDir.resolve("h.script"), script("""
            before("E") { event -> }
            on("E") { event -> val m = event.msg }
            after("E") { event -> }
        """))
        engine.init(tempDir)
        assertEquals(3, engine.getStats().totalHandlers)
        assertDoesNotThrow {
            engine.dispatchEvent("E", mapOf("msg" to "hello"))
        }
    }

    @Test
    fun `undeclared event still works via map get`() {
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("Legacy") { event -> val x = event.x }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        val entry = ScriptEventBus.getHandlersForType("Legacy").first()
        assertDoesNotThrow {
            entry.handler.invokeHandler("Legacy", mapOf("x" to 1))
        }
    }

    @Test
    fun `declare event with special chars in event type`() {
        engine.declareEvent("my-event!") { any("x") }
        Files.writeString(tempDir.resolve("h.script"), script("""
            on("my-event!") { event -> val x = event.x }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertDoesNotThrow {
            engine.dispatchEvent("my-event!", mapOf("x" to 1))
        }
    }
}
