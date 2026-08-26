package kaptor.a2s

import kaptor.a2s.runtime.Rational
import java.math.BigInteger
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RationalTest {

    @Test
    fun `构造时立即约分`() {
        val r = Rational.of(BigInteger("314"), BigInteger("100"))
        assertEquals(BigInteger("157"), r.numerator)
        assertEquals(BigInteger("50"), r.denominator)
    }

    @Test
    fun `负分母归一到分子`() {
        val r = Rational.of(BigInteger.ONE, BigInteger.valueOf(-2))
        assertEquals(BigInteger.valueOf(-1), r.numerator)
        assertEquals(BigInteger("2"), r.denominator)
    }

    @Test
    fun `整数约分为分母1`() {
        val r = Rational.of(BigInteger("10"), BigInteger("5"))
        assertEquals(BigInteger("2"), r.numerator)
        assertEquals(BigInteger.ONE, r.denominator)
    }

    @Test
    @Suppress("KotlinMisorderedAssertEqualsArguments")
    fun `fromDecimalString 精确解析`() {
        assertEquals(Rational.of(BigInteger("157"), BigInteger("50")), Rational.fromDecimalString("3.14"))
        assertEquals(Rational.ONE, Rational.fromDecimalString("1"))
        assertEquals(
            Rational.of(BigInteger("333333333333"), BigInteger("1000000000000")),
            Rational.fromDecimalString("0.333333333333"),
        )
    }

    @Test
    fun `加减乘除运算`() {
        val half = Rational.fromDecimalString("0.5")
        val third = Rational.of(BigInteger.ONE, BigInteger("3"))

        assertEquals(Rational.ONE, half.add(half))
        assertEquals(Rational.ZERO, half.sub(half))
        assertEquals(Rational.fromDecimalString("0.25"), half.mul(half))
        assertEquals(Rational.ONE, half.div(half))
        // 1/3 + 2/3 = 1
        assertEquals(Rational.ONE, third.add(third.mul(Rational.of(2L))))
    }

    @Test
    fun `除法除零抛异常`() {
        assertFailsWith<ArithmeticException> {
            Rational.ONE.div(Rational.ZERO)
        }
    }

    @Test
    fun `比较运算`() {
        val half = Rational.fromDecimalString("0.5")
        val two = Rational.of(2L)
        assertTrue(half < two)
        assertTrue(two > half)
        assertEquals(Rational.fromDecimalString("0.5"), Rational.of(BigInteger.ONE, BigInteger("2")))
    }

    @Test
    fun `相等基于约分后分子分母`() {
        assertEquals(Rational.of(BigInteger("2"), BigInteger("4")), Rational.of(1L).div(Rational.of(2L)))
        assertNotEquals(Rational.ONE, Rational.ZERO)
    }

    @Test
    fun `显示格式`() {
        assertEquals("3", Rational.of(3L).toString())
        assertEquals("157/50 (3.14)", Rational.of(BigInteger("157"), BigInteger("50")).toString())
        assertEquals("1/3 (0.33333333)", Rational.of(BigInteger.ONE, BigInteger("3")).toString())
    }

    @Test
    fun `取模运算`() {
        // 5 mod 3 = 2
        assertEquals(Rational.of(2L), Rational.of(5L).mod(Rational.of(3L)))
        // 7.5 mod 2 = 1.5
        val r = Rational.fromDecimalString("7.5").mod(Rational.of(2L))
        assertEquals(Rational.fromDecimalString("1.5"), r)
    }
}
