package kaptor.a2s

import kaptor.a2s.ir.*
import kaptor.a2s.parser.A2sVisitor
import kaptor.a2s.parser.A2sParser
import kaptor.a2s.parser.A2sLexer
import org.antlr.v4.runtime.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class A2sVisitorTest {

    private fun parse(source: String): A2sScriptFile {
        val lexer = A2sLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = A2sParser(tokens)
        val tree = parser.script()
        return A2sVisitor().visit(tree) as A2sScriptFile
    }

    @Test
    fun `事件处理器转换`() {
        val script = parse(
            """
            on PlayerRightClick { e ->
                if (e.itemStack.key == `diamond`) {
                    e.player.sendMessage("你右键点击了钻石!")
                }
            }
            """
        )
        assertEquals(1, script.handlers.size)
        val handler = script.handlers[0]
        assertEquals("PlayerRightClick", handler.eventType)
        assertEquals(A2sHookType.ON, handler.hookType)
        assertEquals("e", handler.paramName)
        assertEquals(1, handler.body.size)
        assertIs<A2sIf>(handler.body[0])
    }

    @Test
    fun `事件声明与 post`() {
        val script = parse(
            """
            event MyEvent(val count: i32) {
                fun doubled() = count * 2
            }

            on PlayerRightClick { e ->
                post MyEvent(e.itemStack.count)
            }
            """
        )
        assertEquals(1, script.events.size)
        assertEquals("MyEvent", script.events[0].name)
        assertEquals(1, script.events[0].params.size)
        assertEquals("count", script.events[0].params[0].name)
        assertIs<A2sI32>(script.events[0].params[0].type)

        assertEquals(1, script.handlers.size)
        val handler = script.handlers[0]
        assertEquals(1, handler.body.size)
        assertIs<A2sPost>(handler.body[0])
    }

    @Test
    fun `数值字面量解析`() {
        val script = parse(
            """
            on PlayerRightClick { e ->
                val a = 123
                val b = 3.14
                val c = 123_i64
                val d = 3.14_f32
                val f = 1_000_000
            }
            """
        )
        val body = script.handlers[0].body
        assertEquals(5, body.size)
        val decls = body.map { it as A2sVarDecl }
        assertIs<A2sBigIntLiteral>(decls[0].initializer)
        assertIs<A2sRationalLiteral>(decls[1].initializer)
        assertIs<A2sI64Literal>(decls[2].initializer)
        assertIs<A2sF32Literal>(decls[3].initializer)
        val big = decls[4].initializer as A2sBigIntLiteral
        assertEquals("1000000", big.value)
    }

    @Test
    fun `资源引用与数量运算`() {
        val script = parse(
            """
            on PlayerRightClick { e ->
                val total = `diamond` * 10 + `diamond` * 5
            }
            """
        )
        val decl = script.handlers[0].body[0] as A2sVarDecl
        val bin = decl.initializer as A2sBinary
        assertEquals(A2sBinaryOp.PLUS, bin.op)
        assertIs<A2sBinary>(bin.left)
        assertIs<A2sBinary>(bin.right)
    }

    @Test
    fun `字符串模板与插值`() {
        val script = parse(
            """
            on PlayerRightClick { e ->
                val a = "价格: ${'$'}{e.count}"
                val b = "名称: ${'$'}name"
                val c = "字面量: ${'$'}${'$'}name"
                val d = "纯文本"
            }
            """
        )
        val body = script.handlers[0].body
        assertEquals(4, body.size)
        val decls = body.map { it as A2sVarDecl }
        // a: ${e.count} 插值
        assertIs<A2sStringInterpolation>(decls[0].initializer)
        // b: $name 变量引用插值
        assertIs<A2sStringInterpolation>(decls[1].initializer)
        // c: $$ 转义 + 文本，全是纯文本 → 合并为字符串字面量
        assertEquals("字面量: \$name", (decls[2].initializer as A2sStringLiteral).value)
        // d: 纯文本
        assertEquals("纯文本", (decls[3].initializer as A2sStringLiteral).value)
    }

    @Test
    fun `函数声明与类型标注`() {
        val script = parse(
            """
            fun double(n: i64): i64 = n * 2

            fun process(n: i32) {
                val x = n + 1
            }
            """
        )
        assertEquals(2, script.functions.size)
        val f1 = script.functions[0]
        assertEquals("double", f1.name)
        assertEquals(A2sI64, f1.params[0].type)
        assertEquals(A2sI64, f1.returnType)
        assertIs<A2sFunctionBody.Expr>(f1.body)

        val f2 = script.functions[1]
        assertIs<A2sFunctionBody.Block>(f2.body)
    }

    @Test
    fun `控制流语句`() {
        val script = parse(
            """
            on PlayerRightClick { e ->
                while (true) {
                    break
                }
                for (item in listOf(1, 2, 3)) {
                    println(item)
                }
                when (e.itemStack.key) {
                    `diamond` -> println("钻石")
                    else -> println("其他")
                }
                try {
                    println("x")
                } catch (err) {
                    println("err")
                } finally {
                    println("done")
                }
            }
            """
        )
        val body = script.handlers[0].body
        assertEquals(4, body.size)
        assertIs<A2sWhile>(body[0])
        assertIs<A2sFor>(body[1])
        assertIs<A2sWhen>(body[2])
        assertIs<A2sTry>(body[3])
    }

    @Test
    fun `空安全与 elvis`() {
        val script = parse(
            """
            on PlayerRightClick { e ->
                val name = e.player?.name ?: "未知"
            }
            """
        )
        val decl = script.handlers[0].body[0] as A2sVarDecl
        assertIs<A2sBinary>(decl.initializer)
    }
}
