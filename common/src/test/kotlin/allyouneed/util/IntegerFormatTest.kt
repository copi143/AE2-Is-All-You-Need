package allyouneed.util

import allyouneed.util.IntegerFormat.MetricPrefix
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegerFormatTest {

    private val si = IntegerFormat.SI
    private val iec = IntegerFormat.IEC

    // ── SI basics ────────────────────────────────────────────────

    @Test
    fun `SI small values stay plain`() {
        assertEquals("0", si.format(0))
        assertEquals("1", si.format(1))
        assertEquals("999", si.format(999))
    }

    @Test
    fun `SI promotes at base`() {
        assertEquals("1k", si.format(1000))
        assertEquals("1.5k", si.format(1500))
        assertEquals("1M", si.format(1_000_000))
        assertEquals("1G", si.format(1_000_000_000))
    }

    @Test
    fun `SI large BigInteger`() {
        val t = BigInteger.TEN.pow(12) // 1T
        assertEquals("1T", si.format(t))
        val q = BigInteger.TEN.pow(30) // 1Q
        assertEquals("1Q", si.format(q))
    }

    // ── IEC basics ───────────────────────────────────────────────

    @Test
    fun `IEC small values stay plain`() {
        assertEquals("0", iec.format(0))
        assertEquals("1023", iec.format(1023))
    }

    @Test
    fun `IEC promotes at 1024`() {
        assertEquals("1Ki", iec.format(1024))
        assertEquals("1Mi", iec.format(1024L * 1024))
        assertEquals("1Gi", iec.format(1024L * 1024 * 1024))
    }

    // ── façades ──────────────────────────────────────────────────

    @Test
    fun `IntegerFormat facade methods match si and iec`() {
        assertEquals(IntegerFormat.si(4).format(12_345), IntegerFormat.siFormat(12_345L, 4))
        assertEquals(
            IntegerFormat.si(3).format(BigInteger.valueOf(999_999)),
            IntegerFormat.siFormat(BigInteger.valueOf(999_999), 3)
        )
        assertEquals(IntegerFormat.IEC.format(0), IntegerFormat.iecFormatBytes(0))
        assertEquals(IntegerFormat.IEC.format(1024), IntegerFormat.iecFormatBytes(1024))
    }

    @Test
    fun `IEC 256 Ti bytes`() {
        val bytes = 256L * 1024 * 1024 * 1024 * 1024 // 256 TiB
        assertEquals("256Ti", iec.format(bytes))
    }

    // ── threshold ────────────────────────────────────────────────

    @Test
    fun `threshold delays promotion`() {
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf("", "k", "M"),
            threshold = 1500,
            allowOmitDecimal = true,
        )
        // 1000 still below 1500 → stay at base unit as 1000
        assertEquals("1000", fmt.format(1000))
        // 1000k = 1_000_000: at k-level value is 1000 (<1500) so "1000k"
        assertEquals("1000k", fmt.format(1_000_000))
        // 1500k = 1_500_000 → promote to 1.5M
        assertEquals("1.5M", fmt.format(1_500_000))
    }

    @Test
    fun `threshold 500 promotes early`() {
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf("", "k", "M"),
            threshold = 500,
            allowOmitDecimal = true,
        )
        assertEquals("0.5k", fmt.format(500))
        assertEquals("1k", fmt.format(1000))
    }

    // ── allowPlus ────────────────────────────────────────────────

    @Test
    fun `allowPlus encodes overflow levels`() {
        // Sequential levels: "" → k → M → G; beyond G uses G+n
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf("", "k", "M", "G"),
            allowPlus = true,
            allowOmitDecimal = true,
        )
        // 1e9 = 1G
        assertEquals("1G", fmt.format(BigInteger.TEN.pow(9)))
        // 1e12 = 1T = 1G+1
        assertEquals("1G+1", fmt.format(BigInteger.TEN.pow(12)))
        // 1e15 = 1P = 1G+2
        assertEquals("1G+2", fmt.format(BigInteger.TEN.pow(15)))
    }

    @Test
    fun `without allowPlus stays on last unit`() {
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf("", "k", "M", "G"),
            allowPlus = false,
            allowOmitDecimal = true,
        )
        // 1T → 1000G
        assertEquals("1000G", fmt.format(BigInteger.TEN.pow(12)))
    }

    // ── width ────────────────────────────────────────────────────

    @Test
    fun `width limits output length`() {
        val fmt = IntegerFormat.si(4)
        val out = fmt.format(1_234_567)
        assertTrue(out.length <= 4, "got '$out' len=${out.length}")
        assertTrue(out != "ERR", "should fit in width 4, got ERR")
    }

    @Test
    fun `width too small returns errDisplay`() {
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf(""),
            width = 2,
            errDisplay = "E!",
        )
        assertEquals("E!", fmt.format(999)) // "999" is 3 chars
    }

    @Test
    fun `init rejects display longer than width`() {
        assertThrows<IllegalArgumentException> {
            IntegerFormat(1000, listOf(""), width = 2, errDisplay = "ERR")
        }
        assertThrows<IllegalArgumentException> {
            IntegerFormat(1000, listOf(""), width = 2, min = BigInteger.ZERO, minDisplay = "MIN")
        }
    }

    // ── min max ──────────────────────────────────────────────────

    @Test
    fun `min and max clamps to display`() {
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf("", "k"),
            min = BigInteger.TEN,
            max = BigInteger.valueOf(5000),
            minDisplay = "LO",
            maxDisplay = "HI",
        )
        assertEquals("LO", fmt.format(5))
        assertEquals("100", fmt.format(100))
        assertEquals("HI", fmt.format(6000))
    }

    // ── signs ────────────────────────────────────────────────────

    @Test
    fun `negative denied by default`() {
        assertEquals("ERR", si.format(-1))
    }

    @Test
    fun `negative allowed`() {
        val fmt = si.copy(allowNegative = true, allowOmitDecimal = true)
        assertEquals("-1.5k", fmt.format(-1500))
        assertEquals("-1", fmt.format(-1))
    }

    @Test
    fun `show positive sign`() {
        val fmt = si.copy(showPositiveSign = true)
        assertEquals("+1", fmt.format(1))
        assertEquals("0", fmt.format(0))
        assertEquals("+1k", fmt.format(1000))
    }

    @Test
    fun `positive sign consumes width`() {
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf(""),
            width = 3,
            showPositiveSign = true,
            errDisplay = "ER",
        )
        // body width 2 → 99 ok as +99
        assertEquals("+99", fmt.format(99))
        // 100 needs body "100" (3) > 2
        assertEquals("ER", fmt.format(100))
    }

    // ── decimal omit ─────────────────────────────────────────────

    @Test
    fun `allowOmitDecimal strips trailing zeros`() {
        val with = IntegerFormat(1000, listOf("", "k"), allowOmitDecimal = true)
        val without = IntegerFormat(1000, listOf("", "k"), allowOmitDecimal = false, maxDecimalPlaces = 2)
        assertEquals("1k", with.format(1000))
        // 1100 → 1.1k either way when omit trims
        assertEquals("1.1k", with.format(1100))
        val raw = without.format(1000)
        // exact 1000 has zero remainder → no decimal regardless
        assertEquals("1k", raw)
    }

    @Test
    fun `allow omit leading zero`() {
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf("", "k"),
            threshold = 500,
            allowOmitDecimal = true,
            allowOmitLeadingZero = true,
        )
        assertEquals(".5k", fmt.format(500))
    }

    // ── rounding carry promote ───────────────────────────────────

    @Test
    fun `rounding carry can promote unit`() {
        // 999.5k with HalfUp 0 decimal places → 1000k → 1M
        val fmt = IntegerFormat(
            base = 1000,
            postfixes = listOf("", "k", "M"),
            maxDecimalPlaces = 0,
            rounding = IntegerFormat.Rounding.HalfUp,
            allowOmitDecimal = true,
        )
        // 999_500 → 999.5k → rounds to 1000k → 1M
        assertEquals("1M", fmt.format(999_500))
    }

    // ── format overloads ─────────────────────────────────────────

    @Test
    fun `int and long overloads agree`() {
        assertEquals(si.format(12345), si.format(12345L))
        assertEquals(si.format(12345), si.format(BigInteger.valueOf(12345)))
    }

    // ── MetricPrefix ─────────────────────────────────────────────

    @Test
    fun `metric prefix`() {
        println(MetricPrefix.SI)
        println(MetricPrefix.IEC)
        println(MetricPrefix.of(""))
        assertEquals("m", MetricPrefix.SI.level(-1))
        assertEquals("M", MetricPrefix.SI.level(2))
        assertEquals("Mi", MetricPrefix.IEC.level(2))
        val test1 = MetricPrefix.of("")!!
        assertEquals("-2", test1.level(-2))
        assertEquals("-1", test1.level(-1))
        assertEquals("", test1.level(0))
        assertEquals("+1", test1.level(1))
        assertEquals("+2", test1.level(2))
        val test2 = MetricPrefix.of(null, "")!!
        assertEquals(null, test2.level(-2))
        assertEquals(null, test2.level(-1))
        assertEquals("", test2.level(0))
        assertEquals("+1", test2.level(1))
        assertEquals("+2", test2.level(2))
        val test3 = MetricPrefix.of(null, "", null)!!
        assertEquals(null, test3.level(-2))
        assertEquals(null, test3.level(-1))
        assertEquals("", test3.level(0))
        assertEquals(null, test3.level(1))
        assertEquals(null, test3.level(2))
    }

}
