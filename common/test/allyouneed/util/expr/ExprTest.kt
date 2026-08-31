package allyouneed.util.expr

import allyouneed.util.expr.Formula.formula
import allyouneed.util.expr.Formula.parse
import allyouneed.util.expr.Formula.variable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExprTest {

    private val x = variable("x")
    private val y = variable("y")

    // ── 求值 ──────────────────────────────────────────────────────

    @Test
    fun `polynomial evaluates as lambda`() {
        val f = x * x + 3 * x + 2
        assertEquals(42.0, f(5.0))
    }

    @Test
    fun `multi-variable map invocation`() {
        val f = x * y + 1
        assertEquals(7.0, f(mapOf("x" to 2.0, "y" to 3.0)))
    }

    @Test
    fun `vararg bindings`() {
        val f = x * y + 1
        assertEquals(7.0, f("x" to 2.0, "y" to 3.0))
    }

    @Test
    fun `assignable as function type`() {
        val f: (Map<String, Double>) -> Double = x + 1
        assertEquals(3.0, f(mapOf("x" to 2.0)))
    }

    @Test
    fun `default variable is x`() {
        assertEquals(5.0, (x + 3)(2.0))
    }

    @Test
    fun `single non-x variable still binds`() {
        val t = variable("t")
        assertEquals(6.0, (t * 2)(3.0))
    }

    @Test
    fun `constant ignores single value`() {
        assertEquals(7.0, parse("3 + 4")())
    }

    // ── 运算符 / 幂 / 一元负 ──────────────────────────────────────

    @Test
    fun `number on the left`() {
        assertEquals(5.0, (2 * x + 1)(2.0))
    }

    @Test
    fun `power is right associative in text`() {
        assertEquals(512.0, parse("2^3^2")())
    }

    @Test
    fun `unary minus binds looser than power`() {
        assertEquals(-4.0, parse("-x^2")(2.0))
    }

    @Test
    fun `precedence of add mul`() {
        assertEquals(8.0, parse("1 + 2 * 3 + 1")())
    }

    // ── 函数 ──────────────────────────────────────────────────────

    @Test
    fun `sqrt function and getter`() {
        assertEquals(3.0, Formula.sqrt(x)(9.0))
        assertEquals(3.0, x.sqrt(9.0))
    }

    @Test
    fun `min max functions`() {
        assertEquals(2.0, Formula.min(x, y)("x" to 2.0, "y" to 5.0))
        assertEquals(5.0, Formula.max(x, y)("x" to 2.0, "y" to 5.0))
    }

    @Test
    fun `Formula pow equals infix`() {
        assertEquals(8.0, Formula.pow(x, 3)(2.0))
        assertEquals(8.0, (x pow 3)(2.0))
    }

    // ── 组合 ──────────────────────────────────────────────────────

    @Test
    fun `substitute`() {
        val f = x * x + 1
        val g = y + 1
        assertEquals(5.0, f.substitute("x", g)("y" to 1.0))
    }

    @Test
    fun `bind partially evaluates`() {
        val f = x * y + 1
        assertEquals(7.0, f.bind("x" to 2.0)("y" to 3.0))
    }

    @Test
    fun `compose then`() {
        val f = x * 2
        val g = x + 1
        assertEquals(6.0, (f then g)(2.0))
    }

    @Test
    fun `arithmetic combination`() {
        val f = x + 1
        val g = x * 2
        assertEquals(7.0, (f + g)(2.0))
    }

    // ── builder ───────────────────────────────────────────────────

    @Test
    fun `builder scope`() {
        val f = formula { x * x + y }
        assertEquals(5.0, f("x" to 2.0, "y" to 1.0))
    }

    // ── toString / parse 往返 ─────────────────────────────────────

    @Test
    fun `toString produces readable infix`() {
        assertEquals("x^2 + 3 * x + 2", (x.pow(2) + 3 * x + 2).toString())
        assertEquals("(x + 1) * y", ((x + 1) * y).toString())
        assertEquals("x / (y + 3)", (x / (y + 3)).toString())
        assertEquals("-(x + y)", (-(x + y)).toString())
        assertEquals("(x^2)^3", (x.pow(2).pow(3)).toString())
        assertEquals("min(x, y)", Formula.min(x, y).toString())
    }

    @Test
    fun `parse round-trips structurally`() {
        val exprs = listOf<Expr>(
            x + 1,
            x - 1,
            2 * x + 3 * y - 5,
            (x + 1) * (y - 2),
            x / (y + 3),
            x.pow(2) + y.pow(3),
            -(x + y),
            x.sqrt + Formula.min(x, y),
        )
        for (e in exprs) {
            assertEquals(e, parse(e.toString()), "round-trip failed for: $e")
        }
    }

    @Test
    fun `text round-trip via parse then toString`() {
        for (s in listOf("x^2 + 3 * x + 2", "a * b / (c + d)", "2^3^2", "min(x, y) + sqrt(z)")) {
            assertEquals(s, parse(s).toString())
        }
    }

    @Test
    fun `parse numbers decimal and scientific`() {
        assertEquals(3.5, parse("1.5 + 2")())
        assertEquals(1000.0, parse("1e3")())
        assertEquals(0.001, parse("1E-3")())
    }

    // ── 变量与错误 ────────────────────────────────────────────────

    @Test
    fun `variables collects names`() {
        assertEquals(setOf("x", "y"), (x * y + x).variables())
    }

    @Test
    fun `undefined variable throws`() {
        assertFailsWith<IllegalArgumentException> { (x + 1)(mapOf("y" to 2.0)) }
    }

    @Test
    fun `parse errors`() {
        assertFailsWith<IllegalArgumentException> { parse("x +") }
        assertFailsWith<IllegalArgumentException> { parse("(x + 1") }
        assertFailsWith<IllegalArgumentException> { parse("") }
    }

    @Test
    fun `unknown function throws`() {
        assertFailsWith<IllegalArgumentException> { parse("foo(x)")() }
    }

    @Test
    fun `wrong arity throws`() {
        assertFailsWith<IllegalArgumentException> { parse("min(x)")() }
    }
}
