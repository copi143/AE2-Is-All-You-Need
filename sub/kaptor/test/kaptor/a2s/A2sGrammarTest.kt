package kaptor.a2s

import kaptor.a2s.parser.A2sLexer
import kaptor.a2s.parser.A2sParser
import org.antlr.v4.runtime.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class A2sGrammarTest {

    private fun parse(source: String): A2sParser.ScriptContext {
        val lexer = A2sLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        val parser = A2sParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String?,
                e: RecognitionException?
            ) {
                throw AssertionError("语法错误 at $line:$charPositionInLine - $msg")
            }
        })
        return parser.script()
    }

    private fun lexStringTokens(source: String): List<String> {
        val lexer = A2sLexer(CharStreams.fromString(source))
        val tokens = CommonTokenStream(lexer)
        tokens.fill()
        return tokens.tokens.filter { it.type != Token.EOF }
            .map { "${lexer.vocabulary.getSymbolicName(it.type)}:${it.text}" }
    }

    @Test
    fun `事件处理器与资源引用`() {
        parse(
            """
            on PlayerRightClick { e ->
                if (e.itemStack.key == `diamond`) {
                    e.player.sendMessage("你右键点击了钻石!")
                }
            }
            """
        )
    }

    @Test
    fun `事件声明与 post`() {
        parse(
            $$"""
            event MyEvent(val count: i32) {
                fun doubled() = count * 2
            }

            on PlayerRightClick { e ->
                post MyEvent(e.itemStack.count)
            }

            on MyEvent { e ->
                println("收到事件，count 翻倍 = ${e.doubled()}")
            }
            """
        )
    }

    @Test
    fun `阻止操作 deny`() {
        parse(
            """
            on MeNetworkExtract { e ->
                if (e.item.key == `diamond`) {
                    e.deny()
                }
            }
            """
        )
    }

    @Test
    fun `数值字面量与后缀`() {
        parse(
            """
            on PlayerRightClick { e ->
                val count = 123
                val ratio = 3.14
                val fast = 123_i64
                val f = 3.14_f32
                val big = 1_000_000
                val exact = 1 / 3
            }
            """
        )
    }

    @Test
    fun `资源数量运算`() {
        parse(
            """
            on PlayerRightClick { e ->
                val total = `diamond` * 10 + `diamond` * 5
            }
            """
        )
    }

    @Test
    fun `空安全与控制流`() {
        parse(
            """
            on PlayerRightClick { e ->
                val name = e.player?.name ?: "未知"
                val x = e.count!!
                for (item in listOf(1, 2, 3)) {
                    println(item)
                }
                while (true) {
                    break
                }
                when (e.itemStack.key) {
                    `diamond` -> println("钻石")
                    else -> println("其他")
                }
            }
            """
        )
    }

    @Test
    fun `函数声明与显式类型`() {
        parse(
            """
            fun double(n: i64): i64 = n * 2

            fun process(n: i32) {
                val x = n + 1
            }

            on PlayerRightClick { e ->
                val d = double(21)
            }
            """
        )
    }

    @Test
    fun `错误处理`() {
        parse(
            $$"""
            on MeCraftingComplete { e ->
                try {
                    println("合成完成: ${e.result}")
                } catch (err) {
                    println("处理失败: ${err.message}")
                } finally {
                    println("完成")
                }
            }
            """
        )
    }

    @Test
    fun `可空类型标注`() {
        parse(
            """
            event MyEvent(val name: String?) {
                fun len() = name?.length ?: 0
            }
            """
        )
    }

    @Test
    fun `资源类型名作为普通标识符`() {
        parse(
            """
            event MyEvent(val x: Item, val y: Fluid, val z: Energy) {
                fun getX() = x
            }

            on PlayerRightClick { e ->
                val item: Item = `diamond`
                val fluid: Fluid = `fluid|minecraft:water`
            }
            """
        )
    }

    @Test
    fun `字符串美元转义`() {
        parse(
            $$"""
            on PlayerRightClick { e ->
                val a = "价格: $$100"
                val b = "名称: ${name}"
            }
            """
        )
    }

    @Test
    fun `美元转义token不为变量引用`() {
        // $$name 应拆为 LineStrEscapedDollar($$) + LineStrText(name)，而非 LineStrRef($name)
        val tokens = lexStringTokens($$$"\"$$name\"")
        assertEquals(
            listOf(
                "QUOTE_OPEN:\"",
                $$"LineStrEscapedDollar:$$",
                "LineStrText:name",
                "QUOTE_CLOSE:\"",
            ),
            tokens
        )
    }

    @Test
    fun `单个美元后跟标识符仍为变量引用`() {
        // $name 应匹配 LineStrRef($name)
        val tokens = lexStringTokens($$"\"$name\"")
        assertEquals(
            listOf(
                "QUOTE_OPEN:\"",
                $$"LineStrRef:$name",
                "QUOTE_CLOSE:\"",
            ),
            tokens
        )
    }
}
