package allyouneed.util

import allyouneed.util.MetricFormat.MetricPrefix
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricFormatTest {

    private val si = MetricFormat.SI
    private val iec = MetricFormat.IEC

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
    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun `MetricFormat facade methods match si and iec`() {
        assertEquals(MetricFormat.si(4).format(12_345), MetricFormat.siFormat(12_345L, 4))
        assertEquals(
            MetricFormat.si(3).format(BigInteger.valueOf(999_999)),
            MetricFormat.siFormat(BigInteger.valueOf(999_999), 3)
        )
        assertEquals(MetricFormat.IEC.format(0), MetricFormat.iecFormat(0))
        assertEquals(MetricFormat.IEC.format(1024), MetricFormat.iecFormat(1024))
    }

    @Test
    fun `IEC 256 Ti bytes`() {
        val bytes = 256L * 1024 * 1024 * 1024 * 1024 // 256 TiB
        assertEquals("256Ti", iec.format(bytes))
    }

    // ── threshold ────────────────────────────────────────────────

    @Test
    fun `threshold delays promotion`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.of("", "k", "M")!!,
            threshold = 1500,
            allowOmitDecimal = true,
        )
        assertEquals("1000", fmt.format(1000))
        assertEquals("1000k", fmt.format(1_000_000))
        assertEquals("1.5M", fmt.format(1_500_000))
    }

    @Test
    fun `threshold 500 promotes early`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.of("", "k", "M")!!,
            threshold = 500,
            allowOmitDecimal = true,
        )
        assertEquals("0.5k", fmt.format(500))
        assertEquals("1k", fmt.format(1000))
    }

    // ── largerUnlimited ──────────────────────────────────────────

    @Test
    fun `largerUnlimited encodes overflow levels`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.of("", "k", "M", "G")!!,
            allowOmitDecimal = true,
        )
        assertEquals("1G", fmt.format(BigInteger.TEN.pow(9)))
        assertEquals("1G+1", fmt.format(BigInteger.TEN.pow(12)))
        assertEquals("1G+2", fmt.format(BigInteger.TEN.pow(15)))
    }

    @Test
    fun `without largerUnlimited stays on last unit`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.of(null, "", "k", "M", "G", null)!!,
            allowOmitDecimal = true,
        )
        assertEquals("1000G", fmt.format(BigInteger.TEN.pow(12)))
    }

    // ── width ────────────────────────────────────────────────────

    @Test
    fun `width limits output length`() {
        val fmt = MetricFormat.si(4)
        val out = fmt.format(1_234_567)
        assertTrue(out.length <= 4, "got '$out' len=${out.length}")
        assertTrue(out != "ERR", "should fit in width 4, got ERR")
    }

    @Test
    fun `width too small returns errDisplay`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.EMPTY,
            width = 2,
            errDisplay = "E!",
        )
        assertEquals("E!", fmt.format(999))
    }

    @Test
    fun `init rejects display longer than width`() {
        assertThrows<IllegalArgumentException> {
            MetricFormat(1000, MetricPrefix.EMPTY, width = 2, errDisplay = "ERR")
        }
        assertThrows<IllegalArgumentException> {
            MetricFormat(1000, MetricPrefix.EMPTY, width = 2, min = BigInteger.ZERO, minDisplay = "MIN")
        }
    }

    // ── min max ──────────────────────────────────────────────────

    @Test
    fun `min and max clamps to display`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.of("", "k")!!,
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
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.EMPTY,
            width = 3,
            showPositiveSign = true,
            errDisplay = "ER",
        )
        assertEquals("+99", fmt.format(99))
        assertEquals("ER", fmt.format(100))
    }

    // ── decimal omit ─────────────────────────────────────────────

    @Test
    fun `allowOmitDecimal strips trailing zeros`() {
        val with = MetricFormat(1000, MetricPrefix.of("", "k")!!, allowOmitDecimal = true)
        val without = MetricFormat(1000, MetricPrefix.of("", "k")!!, allowOmitDecimal = false, maxDecimalPlaces = 2)
        assertEquals("1k", with.format(1000))
        assertEquals("1.1k", with.format(1100))
        val raw = without.format(1000)
        assertEquals("1k", raw)
    }

    @Test
    fun `allow omit leading zero`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.of("", "k")!!,
            threshold = 500,
            allowOmitDecimal = true,
            allowOmitLeadingZero = true,
        )
        assertEquals(".5k", fmt.format(500))
    }

    // ── rounding carry promote ───────────────────────────────────

    @Test
    fun `rounding carry can promote unit`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.of("", "k", "M")!!,
            maxDecimalPlaces = 0,
            rounding = MetricFormat.Rounding.HalfUp,
            allowOmitDecimal = true,
        )
        assertEquals("1M", fmt.format(999_500))
    }

    // ── format overloads ─────────────────────────────────────────

    @Test
    fun `int and long overloads agree`() {
        assertEquals(si.format(12345), si.format(12345L))
        assertEquals(si.format(12345), si.format(BigInteger.valueOf(12345)))
    }

    // ── rational path ────────────────────────────────────────────

    @Test
    fun `rational demotes to smaller prefix`() {
        val fmt = MetricFormat(
            base = 1000,
            mp = MetricPrefix.SI,
            allowOmitDecimal = true,
        )
        assertEquals("1m", fmt.format(BigDecimal("0.001")))
        assertEquals("500m", fmt.format(BigInteger.ONE, BigInteger.valueOf(2)))
        assertEquals("1.5", fmt.format(BigDecimal("1.5")))
    }

    @Test
    fun `double routes through rational or integer fast path`() {
        assertEquals("1.5k", si.format(1500.0))
        assertEquals("ERR", si.format(Double.NaN))
        assertEquals("500m", si.format(0.5))
    }

    @Test
    fun `BigRational equals integer format when whole`() {
        assertEquals(si.format(1500), si.format(MetricFormat.BigRational.of(1500)))
        assertEquals(si.format(1500), si.format(BigInteger.valueOf(1500), BigInteger.ONE))
    }

    // ── formatParts ──────────────────────────────────────────────

    @Test
    fun `formatParts splits number and unit`() {
        assertEquals("1.5" to "k", si.formatParts(1500))
        assertEquals("999" to "", si.formatParts(999))
        assertEquals("1" to "Ki", iec.formatParts(1024))
        assertEquals("1" to "m", si.formatParts(BigDecimal("0.001")))
        assertEquals("-1.5" to "k", si.copy(allowNegative = true).formatParts(-1500))
        assertEquals("ERR" to "", si.formatParts(-1))
        assertEquals(si.format(12_345_678), si.formatParts(12_345_678).let { it.first + it.second })
    }

    // ── MetricPrefix ─────────────────────────────────────────────

    @Test
    fun `metric prefix`() {
        assertEquals(null, MetricPrefix.of("A", "A", ""))
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
