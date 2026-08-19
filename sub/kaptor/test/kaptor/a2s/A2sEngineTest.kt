package kaptor.a2s

import kaptor.a2s.runtime.A2sEngine
import kaptor.a2s.runtime.A2sEventObject
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
}
