package io.github.copi143.valueschema.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
    fun `clear empties the storage`() {
        val columns = sample()
        columns.clear()
        assertEquals(0, columns.size)
        assertEquals(emptyList(), columns.toList())
    }
}
