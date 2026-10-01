package io.github.copi143.valueschema.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrderNestedTest {

    private fun sampleColumns() = OrderColumns().apply {
        add(Order(1L, Money(10000L, 2), 5))
        add(Order(2L, Money(25000L, 2), 3))
        add(3L, 9900L, 2, 7)
    }

    private fun samplePacked() = OrderPacked().apply {
        add(Order(1L, Money(10000L, 2), 5))
        add(Order(2L, Money(25000L, 2), 3))
        add(3L, 9900L, 2, 7)
    }

    @Test
    fun `nested value type round trips through columns`() {
        val columns = sampleColumns()
        assertEquals(3, columns.size)
        assertEquals(Order(1L, Money(10000L, 2), 5), columns[0])
        assertEquals(Order(3L, Money(9900L, 2), 7), columns[2])
        assertEquals(listOf(10000L, 25000L, 9900L), columns.map { it.price.amount })
    }

    @Test
    fun `nested value type round trips through packed`() {
        val packed = samplePacked()
        assertEquals(3, packed.size)
        assertEquals(Order(2L, Money(25000L, 2), 3), packed[1])
    }

    @Test
    fun `primitive overloads take flattened leaf parameters`() {
        val columns = sampleColumns()
        columns.set(0, 9L, -123456L, 4, 1)
        assertEquals(Order(9L, Money(-123456L, 4), 1), columns[0])
        val packed = samplePacked()
        packed.set(2, 8L, 777L, 0, 42)
        assertEquals(Order(8L, Money(777L, 0), 42), packed[2])
    }

    @Test
    fun `value overload decomposes nested fields without extra copies`() {
        val columns = OrderColumns()
        columns.add(Order(5L, Money(1L, 0), 2))
        assertEquals(Order(5L, Money(1L, 0), 2), columns[0])
    }

    @Test
    fun `updateAt transforms nested field in value style`() {
        val columns = sampleColumns()
        columns.updateAt(1) { it.copy(price = it.price.copy(amount = it.price.amount + 500)) }
        assertEquals(Order(2L, Money(25500L, 2), 3), columns[1])
    }

    @Test
    fun `view exposes flattened leaf properties and rebuilds nested value`() {
        val columns = sampleColumns()
        val view = columns.view(2)
        assertEquals(3L, view.id)
        assertEquals(9900L, view.price_amount)
        assertEquals(2, view.price_scale)
        assertEquals(7, view.qty)
        assertEquals(Order(3L, Money(9900L, 2), 7), view.toValue())
    }

    @Test
    fun `snippet transform mutates nested leaf field wise`() {
        val columns = sampleColumns()
        columns.discount(1, 1000)
        assertEquals(Order(2L, Money(22500L, 2), 3), columns[1])
        val packed = samplePacked()
        packed.discount(0, 500)
        assertEquals(Order(1L, Money(9500L, 2), 5), packed[0])
    }

    @Test
    fun `packed view exposes flattened leaves`() {
        val packed = samplePacked()
        var checked = false
        packed.forEachView { v ->
            if (v.id == 2L) {
                assertEquals(25000L, v.price_amount)
                checked = true
            }
        }
        assertTrue(checked)
    }

    @Test
    fun `filterTo and list api work on nested values`() {
        val columns = sampleColumns()
        val cheap = columns.filtered { it.price_amount < 20000L }
        assertEquals(listOf(Order(1L, Money(10000L, 2), 5), Order(3L, Money(9900L, 2), 7)), cheap.toList())
        val list: List<Order> = columns
        assertTrue(list.contains(Order(2L, Money(25000L, 2), 3)))
    }
}
