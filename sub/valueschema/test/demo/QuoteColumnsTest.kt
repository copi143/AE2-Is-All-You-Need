package io.github.copi143.valueschema.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun Quote.capped(limit: Int): Quote = if (price > limit) copy(price = limit) else this

class QuoteColumnsTest {

    private fun sample(): QuoteColumns = QuoteColumns(2).apply {
        add(Quote(1L, 100, 1000L))
        add(Quote(2L, 200, 2000L))
        add(Quote(3L, 300, 3000L))
    }

    @Test
    fun `add and get round trip`() {
        val columns = sample()
        assertEquals(3, columns.size)
        assertTrue(columns.capacity >= 3)
        assertEquals(Quote(2L, 200, 2000L), columns[1])
    }

    @Test
    fun `set replaces a row`() {
        val columns = sample()
        columns[0] = Quote(9L, 900, 9000L)
        assertEquals(Quote(9L, 900, 9000L), columns[0])
    }

    @Test
    fun `forEach reads rows without materializing`() {
        val columns = sample()
        var sum = 0L
        columns.forEach { q -> sum += q.price }
        assertEquals(600L, sum)
    }

    @Test
    fun `updateAll transforms rows in value style`() {
        val columns = sample()
        columns.updateAll { q -> if (q.price > 150) q.copy(price = q.price / 2) else q }
        assertEquals(Quote(1L, 100, 1000L), columns[0])
        assertEquals(Quote(2L, 100, 2000L), columns[1])
        assertEquals(Quote(3L, 150, 3000L), columns[2])
    }

    @Test
    fun `filterTo copies matching rows`() {
        val columns = sample()
        val big = columns.filtered { it.price > 100 }
        assertEquals(listOf(Quote(2L, 200, 2000L), Quote(3L, 300, 3000L)), big.toList())
    }

    @Test
    fun `grows beyond initial capacity`() {
        val columns = QuoteColumns(1)
        repeat(1000) { i -> columns.add(Quote(i.toLong(), i, i.toLong())) }
        assertEquals(1000, columns.size)
        assertEquals(Quote(999L, 999, 999L), columns[999])
    }

    @Test
    fun `index out of bounds throws`() {
        val columns = sample()
        assertFailsWith<IndexOutOfBoundsException> { columns[3] }
        assertFailsWith<IndexOutOfBoundsException> { columns[-1] }
    }

    @Test
    fun `view exposes fields by name`() {
        val columns = sample()
        val view = columns.view(1)
        assertEquals(2L, view.id)
        assertEquals(200, view.price)
        assertEquals(2000L, view.ts)
        assertEquals(Quote(2L, 200, 2000L), view.toValue())
    }

    @Test
    fun `updateAt applies a transform function to a single element`() {
        val columns = sample()
        columns.updateAt(1) { it.capped(150) }
        columns.updateAt(0) { it.capped(150) }
        assertEquals(Quote(1L, 100, 1000L), columns[0])
        assertEquals(Quote(2L, 150, 2000L), columns[1])
        assertEquals(Quote(3L, 300, 3000L), columns[2])
    }

    @Test
    fun `snippet transform mutates a single element field wise`() {
        val columns = sample()
        columns.cappedAt(1, 150)
        columns.cappedAt(0, 150)
        assertEquals(Quote(1L, 100, 1000L), columns[0])
        assertEquals(Quote(2L, 150, 2000L), columns[1])
    }

    @Test
    fun `snippet transform with multiple params`() {
        val columns = sample()
        columns.scale(0, 2, 5L)
        assertEquals(Quote(1L, 200, 1005L), columns[0])
    }

    @Test
    fun `primitive overloads append and replace without constructing a value`() {
        val columns = sample()
        columns.add(4L, 400, 4000L)
        assertEquals(Quote(4L, 400, 4000L), columns[3])
        columns.set(0, 9L, 900, 9000L)
        assertEquals(Quote(9L, 900, 9000L), columns[0])
    }

    @Test
    fun `clear empties the storage`() {
        val columns = sample()
        columns.clear()
        assertEquals(0, columns.size)
        assertEquals(emptyList(), columns.toList())
    }
}
