package io.github.copi143.valueschema.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuotePackedTest {

    private fun sample(): QuotePacked = QuotePacked(2).apply {
        add(Quote(1L, 100, 1000L))
        add(Quote(2L, 200, 2000L))
        add(Quote(3L, 300, 3000L))
    }

    @Test
    fun `add and get round trip`() {
        val packed = sample()
        assertEquals(3, packed.size)
        assertTrue(packed.capacity >= 3)
        assertEquals(Quote(2L, 200, 2000L), packed[1])
    }

    @Test
    fun `set replaces a row`() {
        val packed = sample()
        packed[0] = Quote(9L, 900, 9000L)
        assertEquals(Quote(9L, 900, 9000L), packed[0])
    }

    @Test
    fun `forEach reads rows without materializing`() {
        val packed = sample()
        var sum = 0L
        packed.forEach { q -> sum += q.price }
        assertEquals(600L, sum)
    }

    @Test
    fun `updateAll transforms rows in value style`() {
        val packed = sample()
        packed.updateAll { q -> if (q.price > 150) q.copy(price = q.price / 2) else q }
        assertEquals(Quote(1L, 100, 1000L), packed[0])
        assertEquals(Quote(2L, 100, 2000L), packed[1])
        assertEquals(Quote(3L, 150, 3000L), packed[2])
    }

    @Test
    fun `filterTo copies matching rows`() {
        val packed = sample()
        val big = packed.filtered { it.price > 100 }
        assertEquals(listOf(Quote(2L, 200, 2000L), Quote(3L, 300, 3000L)), big.toList())
    }

    @Test
    fun `grows beyond initial capacity`() {
        val packed = QuotePacked(1)
        repeat(1000) { i -> packed.add(Quote(i.toLong(), i, i.toLong())) }
        assertEquals(1000, packed.size)
        assertEquals(Quote(999L, 999, 999L), packed[999])
    }

    @Test
    fun `index out of bounds throws`() {
        val packed = sample()
        assertFailsWith<IndexOutOfBoundsException> { packed[3] }
        assertFailsWith<IndexOutOfBoundsException> { packed[-1] }
    }

    @Test
    fun `view exposes fields by name`() {
        val packed = sample()
        val view = packed.view(1)
        assertEquals(2L, view.id)
        assertEquals(200, view.price)
        assertEquals(2000L, view.ts)
        assertEquals(Quote(2L, 200, 2000L), view.toValue())
    }

    @Test
    fun `updateAt applies a transform function to a single element`() {
        val packed = sample()
        packed.updateAt(2) { it.copy(price = it.price + 1, ts = it.ts + 7) }
        assertEquals(Quote(3L, 301, 3007L), packed[2])
        assertEquals(Quote(1L, 100, 1000L), packed[0])
    }

    @Test
    fun `snippet transform mutates a single element field wise`() {
        val packed = sample()
        packed.cappedAt(2, 150)
        packed.cappedAt(0, 150)
        assertEquals(Quote(3L, 150, 3000L), packed[2])
        assertEquals(Quote(1L, 100, 1000L), packed[0])
    }

    @Test
    fun `primitive overloads append and replace without constructing a value`() {
        val packed = sample()
        packed.add(4L, 400, 4000L)
        assertEquals(Quote(4L, 400, 4000L), packed[3])
        packed.set(0, 9L, 900, 9000L)
        assertEquals(Quote(9L, 900, 9000L), packed[0])
    }

    @Test
    fun `clear empties the storage`() {
        val packed = sample()
        packed.clear()
        assertEquals(0, packed.size)
        assertEquals(emptyList(), packed.toList())
    }
}

class MixedPackedTest {

    @Test
    fun `stride reflects compact packing`() {
        assertEquals(3, QuotePacked.STRIDE)
        // l(64) | i(32) | d(64) | f+s+b+bool(32+16+8+1=57) | c(16) = 5 slots
        assertEquals(5, MixedPacked.STRIDE)
    }

    @Test
    fun `all primitive types survive bit packing`() {
        val packed = MixedPacked(1)
        val values = listOf(
            Mixed(Long.MAX_VALUE, Int.MAX_VALUE, Double.MAX_VALUE, Float.MAX_VALUE, Short.MAX_VALUE, Byte.MAX_VALUE, true, 'x'),
            Mixed(Long.MIN_VALUE, Int.MIN_VALUE, Double.MIN_VALUE, Float.MIN_VALUE, Short.MIN_VALUE, Byte.MIN_VALUE, false, '中'),
            Mixed(0L, 0, -0.0, 0.0f, 0, 0, true, ' '),
            Mixed(-1L, -1, Double.NaN, Float.POSITIVE_INFINITY, -1, -1, false, '￿'),
        )
        packed.addAll(values)
        assertEquals(values, packed.toList())
    }

    @Test
    fun `packed updateAll preserves conversions`() {
        val packed = MixedPacked(1)
        packed.add(Mixed(1L, 2, 1.5, 2.5f, 3, 4, false, 'a'))
        packed.updateAll { it.copy(i = it.i * 10, d = it.d * 2.0, bool = !it.bool, c = 'b') }
        assertEquals(Mixed(1L, 20, 3.0, 2.5f, 3, 4, true, 'b'), packed[0])
    }

    @Test
    fun `primitive overloads preserve conversions`() {
        val packed = MixedPacked(1)
        packed.add(1L, 2, 1.5, 2.5f, 3, 4, false, 'a')
        packed.set(0, -1L, -2, -1.5, -2.5f, -3, -4, true, 'b')
        assertEquals(Mixed(-1L, -2, -1.5, -2.5f, -3, -4, true, 'b'), packed[0])
    }
}
