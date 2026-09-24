package io.github.copi143.valueschema.demo

import kotlin.test.Test
import kotlin.test.assertEquals

class EventEnumTest {

    @Test
    fun `columns round trip with enum field`() {
        val columns = EventColumns()
        columns.add(Event(1L, EventKind.Created, true))
        columns.add(id = 2L, kind = EventKind.Deleted, flag = false)
        assertEquals(Event(1L, EventKind.Created, true), columns[0])
        assertEquals(Event(2L, EventKind.Deleted, false), columns[1])
    }

    @Test
    fun `set with enum value`() {
        val columns = EventColumns()
        columns.add(Event(1L, EventKind.Created, true))
        columns.set(0, id = 9L, kind = EventKind.Updated, flag = false)
        assertEquals(Event(9L, EventKind.Updated, false), columns[0])
    }

    @Test
    fun `view exposes enum type`() {
        val columns = EventColumns()
        columns.add(Event(1L, EventKind.Deleted, true))
        var seen: EventKind? = null
        columns.forEachView { seen = it.kind }
        assertEquals(EventKind.Deleted, seen)
    }

    @Test
    fun `transform snippet writes enum field`() {
        val columns = EventColumns()
        columns.add(Event(1L, EventKind.Created, true))
        columns.markDeleted(0)
        assertEquals(EventKind.Deleted, columns[0].kind)
    }

    @Test
    fun `resize fills enum default ordinal`() {
        val columns = EventColumns()
        columns.resize(2)
        assertEquals(EventKind.Updated, columns[0].kind)
        assertEquals(EventKind.Updated, columns[1].kind)
    }

    @Test
    fun `packed round trip with enum field`() {
        val packed = EventPacked()
        packed.add(Event(1L, EventKind.Created, true))
        packed.add(id = 2L, kind = EventKind.Deleted, flag = false)
        assertEquals(Event(1L, EventKind.Created, true), packed[0])
        assertEquals(Event(2L, EventKind.Deleted, false), packed[1])
        packed.markDeleted(0)
        assertEquals(EventKind.Deleted, packed[0].kind)
    }

    @Test
    fun `packed resize fills enum default`() {
        val packed = EventPacked()
        packed.resize(2)
        assertEquals(EventKind.Updated, packed[0].kind)
        assertEquals(EventKind.Updated, packed[1].kind)
    }

    @Test
    fun `updateAll value style works with enum fields`() {
        val columns = EventColumns()
        columns.add(Event(1L, EventKind.Created, false))
        columns.updateAll { it.copy(kind = EventKind.Updated, flag = true) }
        assertEquals(Event(1L, EventKind.Updated, true), columns[0])
    }
}
