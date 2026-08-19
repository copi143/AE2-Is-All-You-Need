package kaptor.a2s

import kaptor.a2s.compiler.A2sCompiler
import kaptor.a2s.parser.A2sLexer
import kaptor.a2s.parser.A2sParser
import kaptor.a2s.parser.A2sVisitor
import org.antlr.v4.runtime.*
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
        val hcLookup = lookup.defineHiddenClass(compiled.scriptClass, true, MethodHandles.Lookup.ClassOption.NESTMATE)
        val clazz = hcLookup.lookupClass()
        val instance = hcLookup.findConstructor(clazz, MethodType.methodType(Void.TYPE)).invoke()

        // 用反射查找方法（参数类型由实际传入值推断）
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
}
