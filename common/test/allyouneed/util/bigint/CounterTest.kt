package allyouneed.util.bigint

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CounterTest {

    private val two128 = BigInteger.ONE.shiftLeft(128)
    private val two129 = BigInteger.ONE.shiftLeft(129)
    private val two64 = BigInteger.ONE.shiftLeft(64)

    companion object {
        private val MAX_U64_BI: BigInteger = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
    }

    // ── 构造 / 表示 ──────────────────────────────────────────────

    @Test
    fun `U64 value keeps lo only`() {
        val c = Counter.of(42L)
        assertEquals("42", c.toString())
        assertFalse(c.isBig)
        assertFalse(c.isU128)
        assertTrue(c.isU64)
    }

    @Test
    fun `BigInteger within U128 normalizes without bi`() {
        val hi = BigInteger.ONE.shiftLeft(64).add(BigInteger.TWO)
        val c = Counter.of(hi)
        assertEquals(hi, c.toBigInteger())
        assertFalse(c.isBig)
        assertTrue(c.isU128)
    }

    @Test
    fun `BigInteger beyond U128 keeps bi`() {
        val bi = two129.add(BigInteger.ONE)
        val c = Counter.of(bi)
        assertEquals(bi, c.toBigInteger())
        assertTrue(c.isBig)
    }

    @Test
    fun `negative input rejected`() {
        assertThrows<IllegalArgumentException> { Counter.of(-1L) }
        assertThrows<IllegalArgumentException> { Counter.of(BigInteger.ONE.negate()) }
    }

    // ── 加法 ─────────────────────────────────────────────────────

    @Test
    fun `add within U64`() {
        assertEquals(Counter.of(30L), Counter.of(12L) + Counter.of(18L))
        assertEquals(BigInteger.valueOf(30), (Counter.of(12L) + 18L).toBigInteger())
    }

    @Test
    fun `add carries U64 into U128`() {
        val sum = Counter.MAX_U64 + Counter.of(1L)
        assertEquals(two64, sum.toBigInteger())
        assertFalse(sum.isBig)
        assertTrue(sum.isU128)
    }

    @Test
    fun `add exactly at 2^128 promotes to big integer`() {
        // (2^128 - 2^64) + 2^64 == 2^128
        val a = Counter.fromRaw(0UL, ULong.MAX_VALUE)
        val b = Counter.fromU64(0UL, 1UL)
        val sum = a + b
        assertEquals(two128, sum.toBigInteger())
        assertTrue(sum.isBig)
    }

    @Test
    fun `u128 high overflow with low carry promotes to big integer`() {
        // MAX_U128 == 2^128 - 1
        val a = Counter.MAX_U128
        // 2^128 - 2^64 + 1
        val b = Counter.fromRaw(1UL, ULong.MAX_VALUE)
        val expected = two129 - two64 // == 2^129 - 2^64
        val sum = a + b
        assertEquals(expected, sum.toBigInteger())
        assertTrue(sum.isBig)
    }

    @Test
    fun `two U128 max values promote to big integer`() {
        val sum = Counter.MAX_U128 + Counter.MAX_U128
        assertEquals(two129 - BigInteger.TWO, sum.toBigInteger())
        assertTrue(sum.isBig)
    }

    @Test
    fun `high overflow without low carry stays u128`() {
        // (2^128 - 2^64) + 2 stays representable
        val a = Counter.fromRaw(0UL, ULong.MAX_VALUE)
        val sum = a + Counter.of(2L)
        assertEquals(two128 - two64 + BigInteger.TWO, sum.toBigInteger())
        assertFalse(sum.isBig)
        assertTrue(sum.isU128)
    }

    @Test
    fun `top U128 value stays representable`() {
        val a = Counter.fromRaw(0UL, ULong.MAX_VALUE)
        val b = Counter.fromU64(0UL, ULong.MAX_VALUE)
        // (2^128 - 2^64) + (2^128 - 2^64) == 2^129 - 2^65，仍需 BigInteger
        val sum = a + b
        assertEquals(two129 - two64.multiply(BigInteger.TWO), sum.toBigInteger())
        assertTrue(sum.isBig)
    }

    @Test
    fun `big integer path addition is exact`() {
        val x = Counter.of(two128.add(BigInteger.valueOf(5)))
        val y = Counter.of(BigInteger.valueOf(7))
        assertEquals(two128.add(BigInteger.valueOf(12)), (x + y).toBigInteger())
        assertEquals(two128.add(BigInteger.valueOf(12)), (x + 7L).toBigInteger())
        assertEquals(two128.add(BigInteger.valueOf(12)), (x + BigInteger.valueOf(7)).toBigInteger())
    }

    // ── 减法 / 乘法 / 除法 ──────────────────────────────────────

    @Test
    fun `subtract within u128`() {
        val a = Counter.of(two64.add(ULong.MAX_VALUE.toBigInteger())) // 2^64 + (2^64 - 1)
        val b = Counter.of(two64)
        assertEquals(ULong.MAX_VALUE.toBigInteger(), (a - b).toBigInteger())
    }

    @Test
    fun `subtract result negative throws`() {
        assertThrows<IllegalArgumentException> { Counter.of(1L) - Counter.of(2L) }
    }

    @Test
    fun `times agrees with big integer`() {
        val a = Counter.fromRaw(0UL, 1UL) // 2^64
        val prod = a * Counter.of(3L)
        assertEquals(two64.multiply(BigInteger.valueOf(3)), prod.toBigInteger())
        assertTrue(prod.isU128)
    }

    @Test
    fun `div rounded toward zero like big integer`() {
        val a = Counter.of(ULong.MAX_VALUE.toBigInteger() + BigInteger.valueOf(1)) // 2^64
        val half = BigInteger.ONE.shiftLeft(63) // 2^64 / 2
        assertEquals(half, (a / Counter.of(2L)).toBigInteger())
        assertEquals(half, (a / 2L).toBigInteger())
        assertEquals(BigInteger.ZERO, (a % Counter.of(2L)).toBigInteger())
    }

    @Test
    fun `division by zero throws`() {
        assertThrows<IllegalArgumentException> { Counter.of(1L) / Counter.ZERO }
    }

    // ── 饱和 / 比较 / 哈希 ──────────────────────────────────────

    @Test
    fun `saturation caps at respective max`() {
        assertEquals(Long.MAX_VALUE, Counter.MAX_U128.longSaturated)
        assertEquals(Int.MAX_VALUE, Counter.MAX_U128.intSaturated)
        assertEquals(42L, Counter.of(42L).longSaturated)
    }

    @Test
    fun `compare and equals are value based`() {
        val a = Counter.of(two64)
        val b = Counter.fromRaw(0UL, 1UL)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(Counter.MAX_U128 > Counter.of(1L))
        assertTrue(Counter.of(1L) < Counter.MAX_U128)
    }

    private fun ULong.toBigInteger(): BigInteger = BigInteger(this.toString())
}