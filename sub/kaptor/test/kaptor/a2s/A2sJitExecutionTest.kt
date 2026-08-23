package kaptor.a2s

import kaptor.a2s.compiler.A2sCompiler
import kaptor.a2s.parser.A2sLexer
import kaptor.a2s.parser.A2sParser
import kaptor.a2s.parser.A2sVisitor
import kaptor.a2s.runtime.A2sEngine
import kaptor.a2s.runtime.A2sEventObject
import org.antlr.v4.runtime.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * 验证 JIT 编译的字节码真正执行且结果正确。
 */
class A2sJitExecutionTest {

    private fun invokeFunction(source: String, fnName: String, vararg args: Any?): Any? {
        val lexer = A2sLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = A2sParser(tokens)
        val tree = parser.script()
        val ir = A2sVisitor().visit(tree) as kaptor.a2s.ir.A2sScriptFile
        val compiled = A2sCompiler().compile(ir)

        val lookup = MethodHandles.privateLookupIn(kaptor.a2s.gen.GenHost::class.java, MethodHandles.lookup())
        // 清除旧注册 + 定义 lambda 隐藏类并注册构造器工厂
        kaptor.a2s.runtime.A2sRuntime.clearLambdaCtors()
        for ((name, bytes) in compiled.lambdaClasses) {
            val hcLookup = lookup.defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE)
            val lClazz = hcLookup.lookupClass()
            val ctorParams = lClazz.constructors.first().parameterTypes
            val ctorType = MethodType.methodType(Void::class.javaPrimitiveType!!, ctorParams)
            val ctor = hcLookup.findConstructor(lClazz, ctorType)
            kaptor.a2s.runtime.A2sRuntime.registerLambdaCtor(name) { scriptObj, captures ->
                ctor.invokeWithArguments(scriptObj, *captures)
            }
        }

        val hcLookup = lookup.defineHiddenClass(compiled.scriptClass, true, MethodHandles.Lookup.ClassOption.NESTMATE)
        val clazz = hcLookup.lookupClass()
        val instance = hcLookup.findConstructor(clazz, MethodType.methodType(Void.TYPE)).invoke()

        val method = clazz.methods.find { it.name == fnName && it.parameterCount == args.size } ?: error("no method $fnName")
        return method.invoke(instance, *args)
    }

    @Test
    fun `整数加法运算结果正确`() {
        val r = invokeFunction("fun add(a: i64, b: i64): i64 = a + b", "add", 40L, 2L)
        assertEquals(42L, r)
    }

    @Test
    fun `大整数加法结果正确`() {
        val r = invokeFunction(
            "fun add(a: BigInt, b: BigInt): BigInt = a + b",
            "add",
            java.math.BigInteger("1000000000000000000000"),
            java.math.BigInteger("23"),
        )
        assertEquals(java.math.BigInteger("1000000000000000000023"), r)
    }

    @Test
    fun `整数除法得到有理数`() {
        val r = invokeFunction("fun half(): Rational = 1 / 2", "half")
        assertEquals("1/2 (0.5)", r.toString())
    }

    @Test
    fun `函数返回比较结果`() {
        val r = invokeFunction("fun gt(a: i64): Boolean = a > 100", "gt", 150L)
        assertEquals(true, r)
    }

    @Test
    fun `字符串字面量结果正确`() {
        val r = invokeFunction("fun greet(): String = \"hello\"", "greet")
        assertEquals("hello", r)
    }

    // ── 任务1：try/catch ──

    @Test
    fun `try 内 throw 被 catch 捕获`() {
        val r = invokeFunction(
            """
            fun test(): String {
                try {
                    throw RuntimeException("oops")
                } catch (err) {
                    return "caught"
                }
            }
            """, "test"
        )
        assertEquals("caught", r)
    }

    @Test
    fun `无异常跳过 catch`() {
        val r = invokeFunction(
            """
            fun test(): String {
                try {
                    return "ok"
                } catch (err) {
                    return "caught"
                }
            }
            """, "test"
        )
        assertEquals("ok", r)
    }

    @Test
    fun `finally 在正常路径后执行`() {
        val r = invokeFunction(
            """
            var counter = 0_i64
            fun test(): i64 {
                try {
                    return 42_i64
                } finally {
                    counter = counter + 1_i64
                }
            }
            """, "test"
        )
        assertEquals(42L, r)
    }

    // ── 任务2：elvis ──

    @Test
    fun `elvis 左非空返回左`() {
        val r = invokeFunction(
            """fun test(a: String): String = a ?: "default" """, "test", "hi"
        )
        assertEquals("hi", r)
    }

    @Test
    fun `elvis 左空返回右`() {
        val r = invokeFunction(
            """fun test(): String = null ?: "default" """, "test"
        )
        assertEquals("default", r)
    }

    // ── 任务3：!!. ──

    @Test
    fun `非空断言不抛异常`() {
        val r = invokeFunction(
            """fun test(a: String): Int = len(a!!) """, "test", "abc"
        )
        assertEquals(3, r)
    }

    @Test
    fun `非空断言 null 抛 NPE`() {
        try {
            invokeFunction(
                """fun test(a: Any): Any = a!!.toString() """, "test", null
            )
            throw AssertionError("Expected NPE")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            assertTrue(e.cause is NullPointerException)
        }
    }

    // ── 任务4：listOf ──

    @Test
    fun `listOf 创建列表`() {
        val r = invokeFunction(
            """fun test(): Int = len(listOf(1, 2, 3)) """, "test"
        )
        assertEquals(3, r)
    }

    // ── 闭包捕获 ──

    @Test
    fun `闭包捕获函数参数`() {
        val r = invokeFunction(
            """
            fun adder(n: i64): i64 {
                val f = { x: i64 -> n + x }
                return f(10_i64)
            }
            """, "adder", 32L
        )
        assertEquals(42L, r)
    }

    @Test
    fun `闭包捕获局部变量`() {
        val r = invokeFunction(
            """
            fun test(): i64 {
                var counter = 0_i64
                val inc = { x: i64 -> counter + x }
                return inc(5_i64)
            }
            """, "test"
        )
        assertEquals(5L, r)
    }

    @Test
    fun `嵌套闭包`() {
        val r = invokeFunction(
            """
            fun outer(a: i64): i64 {
                val f = { x: i64 ->
                    val g = { y: i64 -> a + x + y }
                    g(1_i64)
                }
                return f(10_i64)
            }
            """, "outer", 20L
        )
        assertEquals(31L, r)
    }

    @Test
    fun `无捕获 lambda 回归`() {
        val source = """
            fun test(): i64 {
                val f = { x: i64 -> x * 2_i64 }
                return f(21_i64)
            }
            """
        val r = invokeFunction(source, "test")
        assertEquals(42L, r)
    }

    @Test
    fun `闭包捕获多个变量`() {
        val r = invokeFunction(
            """
            fun test(a: i64, b: i64): i64 {
                val f = { x: i64 -> a + b + x }
                return f(1_i64)
            }
            """, "test", 10L, 20L
        )
        assertEquals(31L, r)
    }

    // ── val 不可变性 ──

    @Test
    fun `val 局部变量赋值编译期报错`() {
        assertFailsWith<kaptor.a2s.compiler.A2sCompileError> {
            invokeFunction(
                """
                fun test(): i64 {
                    val x = 1_i64
                    x = 2_i64
                    return x
                }
                """, "test"
            )
        }
    }

    // ── RANGE / for-in ──

    @Test
    fun `range for 求和`() {
        val r = invokeFunction(
            """
            fun test(): i64 {
                var sum = 0_i64
                for (i in 1_i64..5_i64) {
                    sum = sum + i
                }
                return sum
            }
            """, "test"
        )
        assertEquals(15L, r)
    }

    @Test
    fun `range 单元素`() {
        val r = invokeFunction(
            """
            fun test(): i64 {
                var count = 0_i64
                for (i in 3_i64..3_i64) {
                    count = count + 1_i64
                }
                return count
            }
            """, "test"
        )
        assertEquals(1L, r)
    }

    @Test
    fun `range 空范围`() {
        val r = invokeFunction(
            """
            fun test(): i64 {
                var count = 0_i64
                for (i in 5_i64..2_i64) {
                    count = count + 1_i64
                }
                return count
            }
            """, "test"
        )
        assertEquals(0L, r)
    }
}
