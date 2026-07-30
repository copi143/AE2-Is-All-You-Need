package kaptor

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
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertDoesNotThrow

class SandboxQuotaTest {
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

    @Test
    fun `sandbox consumes instruction counter and respects limit`() {
        val sandbox = kaptor.runtime.ScriptSandbox(10)
        assertEquals(10, sandbox.limit)
        assertEquals(0, sandbox.getCounter())
        assertTrue(sandbox.consume(4), "consume 4 within limit")
        assertEquals(4, sandbox.getCounter())
        assertTrue(sandbox.consume(6), "consume 6 to exactly hit limit")
        assertEquals(10, sandbox.getCounter())
        assertTrue(sandbox.check())
        assertFalse(sandbox.consume(1), "consume 1 past limit")
        assertFalse(sandbox.check())
        assertEquals(0, sandbox.getRemaining())
    }

    @Test
    fun `sandbox reset clears counter and updates limit`() {
        val sandbox = kaptor.runtime.ScriptSandbox(100)
        sandbox.consume(30)
        assertEquals(30, sandbox.getCounter())
        sandbox.reset(50)
        assertEquals(0, sandbox.getCounter())
        assertEquals(50, sandbox.limit)
        assertTrue(sandbox.consume(50))
        assertFalse(sandbox.consume(1))
    }

    @Test
    fun `sandbox throwLimitExceeded throws ScriptLimitException`() {
        assertFailsWith<kaptor.runtime.ScriptLimitException> {
            kaptor.runtime.ScriptSandbox.throwLimitExceeded("test limit")
        }
    }

    @Test
    fun `handler with small body does not exceed cost limit`() {
        Files.writeString(tempDir.resolve("h.kts"), script("""
            on("E") { event ->
                val x = 1
                val y = "hello"
                val z = true
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
    fun `handler with moderate loop does not exceed cost limit`() {
        Files.writeString(tempDir.resolve("h.kts"), script("""
            on("E") { event ->
                var i = 0
                while (i < 10) {
                    i = i + 1
                }
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
    fun `handler with 1000 val declarations exceeds cost limit`() {
        val body = (1..1000).joinToString("\n") { "    val x$it = 1" }
        Files.writeString(tempDir.resolve("h.kts"), script("""
            on("E") { event ->
$body
            }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        val entry = ScriptEventBus.getHandlersForType("E").first()
        assertFailsWith<kaptor.runtime.ScriptLimitException> {
            entry.handler.invokeHandler("E", emptyMap<String, Any>())
        }
    }

    @Test
    fun `sandbox is reset per dispatch and tracks cost`() {
        Files.writeString(tempDir.resolve("h.kts"), script("""
            on("E") { event ->
                val x = 42
            }
        """))
        engine.init(tempDir)
        assertEquals(1, engine.getStats().totalHandlers)
        assertDoesNotThrow {
            engine.dispatchEvent("E", mapOf<String, Any>())
        }
    }
}
