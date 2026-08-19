package kaptor.a2s

import kaptor.a2s.ir.*
import kaptor.a2s.runtime.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class A2sEngineTest {

    class TestEvent(val name: String) : A2sEventObject()

    @Test
    fun `编译并执行简单事件处理器`() {
        val engine = A2sEngine()

        // 注册 TestEvent 作为内置事件类型
        val lookup = MethodHandles.lookup()
        engine.registerEventClass(
            "TestEvent",
            TestEvent::class.java,
            lookup.findConstructor(TestEvent::class.java, MethodType.methodType(Void.TYPE, String::class.java))
        )

        // 脚本处理 TestEvent，通过字段访问读取 name
        val ok = engine.loadScript(
            """
            on TestEvent { e ->
                val n = e.name
            }
            """
        )
        assertTrue(ok)

        // 分发事件，不应抛异常（invoke 捕获异常）
        engine.dispatch("TestEvent", TestEvent("hello"))
    }

    @Test
    fun `编译简单脚本成功`() {
        val engine = A2sEngine()
        val ok = engine.loadScript(
            """
            val threshold = 100

            fun check(n: i64): Boolean = n > threshold

            on PlayerRightClick { e ->
                val x = 1 + 2
                println("hello")
            }
            """
        )
        assertTrue(ok)
    }

    @Test
    fun `数值运算脚本编译`() {
        val engine = A2sEngine()
        val ok = engine.loadScript(
            """
            on PlayerRightClick { e ->
                val a = 123
                val b = 3.14
                val c = a + a
                val d = b * 2
                val f = 123_i64 + 456_i64
            }
            """
        )
        assertTrue(ok)
    }

    @Test
    fun `事件声明与 post`() {
        val engine = A2sEngine()
        val ok = engine.loadScript(
            """
            event MyEvent(val count: i32) {
                fun doubled() = count * 2
            }

            on PlayerRightClick { e ->
                post MyEvent(e.count)
            }
            """
        )
        assertTrue(ok)
    }

    // ── 桥接接口测试 ──

    /** Mock 事件类：用于桥接测试。 */
    class MockInsertEvent(
        val player: Any?,
        val slot: Int,
        val amount: Long,
    ) : A2sEventObject()

    class MockNetworkFormedEvent(
        val networkId: String,
    ) : A2sEventObject()

    /** Mock 桥接实现。 */
    private val mockBridge = object : A2sEventBridge {
        override fun registeredEvents(): Map<String, EventSchema> = BuiltinEvents.SCHEMA

        override fun createEvent(eventType: String, data: Map<String, Any?>): A2sEventObject {
            return when (eventType) {
                "MeNetworkInsert" -> MockInsertEvent(
                    data["player"], data["slot"] as Int, data["amount"] as Long
                )
                "MeNetworkFormed" -> MockNetworkFormedEvent(
                    data["networkId"] as String
                )
                else -> error("unsupported: $eventType")
            }
        }
    }

    @Test
    fun `桥接注册 + 加载脚本`() {
        val engine = A2sEngine()
        engine.bridge = mockBridge

        // 注册 MockInsertEvent 的字节码类和构造器（模拟桥接层预注册）
        val lookup = MethodHandles.lookup()
        engine.registerEventClass(
            "MeNetworkInsert",
            MockInsertEvent::class.java,
            lookup.findConstructor(
                MockInsertEvent::class.java,
                MethodType.methodType(Void.TYPE, Object::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            )
        )

        val ok = engine.loadScript(
            """
            event MeNetworkInsert(val player: Any, val slot: i32, val amount: i64)

            on MeNetworkInsert { e ->
                val s = e.slot
            }
            """
        )
        assertTrue(ok, "脚本加载成功")
    }

    @Test
    fun `dispatchFromMap 调用 handler`() {
        val engine = A2sEngine()
        engine.bridge = mockBridge

        val lookup = MethodHandles.lookup()
        engine.registerEventClass(
            "MeNetworkInsert",
            MockInsertEvent::class.java,
            lookup.findConstructor(
                MockInsertEvent::class.java,
                MethodType.methodType(Void.TYPE, Object::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            )
        )

        val ok = engine.loadScript(
            """
            event MeNetworkInsert(val player: Any, val slot: i32, val amount: i64)

            before MeNetworkInsert { e ->
                val _ = e.slot
            }
            """
        )
        assertTrue(ok)

        engine.dispatchFromMap("MeNetworkInsert", mapOf(
            "player" to "testPlayer",
            "slot" to 5,
            "amount" to 100L,
        ))
    }

    @Test
    fun `deny 生效`() {
        val engine = A2sEngine()
        engine.bridge = mockBridge

        val lookup = MethodHandles.lookup()
        engine.registerEventClass(
            "MeNetworkInsert",
            MockInsertEvent::class.java,
            lookup.findConstructor(
                MockInsertEvent::class.java,
                MethodType.methodType(Void.TYPE, Object::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            )
        )

        val ok = engine.loadScript(
            """
            event MeNetworkInsert(val player: Any, val slot: i32, val amount: i64)

            on MeNetworkInsert { e ->
                if (e.amount > 50_i64) {
                    e.deny()
                }
            }
            """
        )
        assertTrue(ok)

        val event = mockBridge.createEvent("MeNetworkInsert", mapOf(
            "player" to "testPlayer",
            "slot" to 0,
            "amount" to 100L,
        ))
        engine.dispatch("MeNetworkInsert", event)
        assertTrue(event.isDenied, "amount > 50 时应被 deny")
    }

    @Test
    fun `unregisterAll 清空 handler`() {
        val engine = A2sEngine()
        engine.bridge = mockBridge

        val lookup = MethodHandles.lookup()
        engine.registerEventClass(
            "MeNetworkFormed",
            MockNetworkFormedEvent::class.java,
            lookup.findConstructor(MockNetworkFormedEvent::class.java, MethodType.methodType(Void.TYPE, String::class.java))
        )

        val ok = engine.loadScript(
            """
            event MeNetworkFormed(val networkId: String)

            on MeNetworkFormed { e -> }
            """
        )
        assertTrue(ok)

        engine.unregisterAll()

        // 分发不应报错（无 handler 可执行）
        engine.dispatch("MeNetworkFormed", MockNetworkFormedEvent("net-1"))
    }

    // ── 引擎完善测试 ──

    @Test
    fun `unregisterAll 完整清理`() {
        val engine = A2sEngine()
        engine.bridge = mockBridge

        val lookup = MethodHandles.lookup()
        engine.registerEventClass(
            "MeNetworkInsert",
            MockInsertEvent::class.java,
            lookup.findConstructor(
                MockInsertEvent::class.java,
                MethodType.methodType(Void.TYPE, Object::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            )
        )

        engine.loadScript(
            """
            event MeNetworkInsert(val player: Any, val slot: i32, val amount: i64)
            on MeNetworkInsert { e -> }
            """, "script1"
        )
        engine.loadScript(
            """
            event MeNetworkFormed(val networkId: String)
            on MeNetworkFormed { e -> }
            """, "script2"
        )

        val statsBefore = engine.stats()
        assertEquals(2, statsBefore.loadedScripts)

        engine.unregisterAll()

        val statsAfter = engine.stats()
        assertEquals(0, statsAfter.loadedScripts)
        assertEquals(0, statsAfter.totalHandlers)
        assertEquals(0, statsAfter.registeredEvents)
        assertTrue(engine.errors.isEmpty())
    }

    @Test
    fun `loadScript 失败不残留`() {
        val engine = A2sEngine()

        val eventsBefore = engine.stats().registeredEvents

        // 故意写错语法
        val ok = engine.loadScript("fun broken( = ==", "bad_script")
        assertFalse(ok)

        // 错误历史应有记录
        assertTrue(engine.errors.isNotEmpty())
        assertTrue(engine.errors.last().phase == A2sError.Phase.PARSE || engine.errors.last().phase == A2sError.Phase.COMPILE)
        assertEquals("bad_script", engine.errors.last().scriptName)

        // 不应有新增事件类
        assertEquals(eventsBefore, engine.stats().registeredEvents)
    }

    @Test
    fun `loadScript 同名覆盖`() {
        val engine = A2sEngine()

        engine.loadScript(
            """
            event MyEvent(val count: i32)
            before MyEvent { e -> }
            """, "my_script"
        )
        assertEquals(1, engine.stats().totalHandlers)

        // 同名重载
        engine.loadScript(
            """
            event MyEvent(val count: i32)
            before MyEvent { e -> }
            after MyEvent { e -> }
            """, "my_script"
        )
        // 旧 handler 被替换，新有 before + after = 2
        assertEquals(2, engine.stats().totalHandlers)
        assertEquals(1, engine.stats().loadedScripts)
    }

    @Test
    fun `unloadScript 只移除目标`() {
        val engine = A2sEngine()

        engine.loadScript(
            """
            event MyEvent(val count: i32)
            on MyEvent { e -> }
            """, "script_a"
        )
        engine.loadScript(
            """
            event MyEvent(val count: i32)
            before MyEvent { e -> }
            """, "script_b"
        )
        assertEquals(2, engine.stats().totalHandlers)

        val removed = engine.unloadScript("script_a")
        assertTrue(removed)
        assertEquals(1, engine.stats().totalHandlers)

        // script_b 的 handler 仍在
        val stats = engine.stats()
        assertEquals(1, stats.loadedScripts)

        // 卸载不存在的脚本
        assertFalse(engine.unloadScript("nonexistent"))
    }

    @Test
    fun `reloadScript 替换`() {
        val engine = A2sEngine()

        engine.loadScript(
            """
            event MyEvent(val count: i32)
            on MyEvent { e -> }
            """, "target"
        )
        val handlersBefore = engine.stats().totalHandlers
        assertEquals(1, handlersBefore)

        val ok = engine.reloadScript(
            "target",
            """
            event MyEvent(val count: i32)
            before MyEvent { e -> }
            after MyEvent { e -> }
            """
        )
        assertTrue(ok)
        assertEquals(2, engine.stats().totalHandlers)
        assertEquals(1, engine.stats().loadedScripts)
    }

    @Test
    fun `错误历史记录`() {
        val engine = A2sEngine()

        engine.loadScript("fun broken 123 @#@", "err1")
        engine.loadScript("fun also_bad $$$", "err2")
        engine.loadScript("fun another_bad ***", "err3")

        val errors = engine.errors
        assertEquals(3, errors.size)
        assertEquals("err1", errors[0].scriptName)
        assertEquals("err2", errors[1].scriptName)
        assertEquals("err3", errors[2].scriptName)

        engine.clearErrors()
        assertTrue(engine.errors.isEmpty())
    }
}
