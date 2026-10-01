package io.github.copi143.valueschema.demo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScratchResizeTest {

    @Test
    fun `resize fills new rows with defaults`() {
        val columns = ScratchColumns(1)
        columns.add(Scratch(7, 8, active = false, flag = true))
        columns.resize(3)
        assertEquals(3, columns.size)
        assertEquals(Scratch(7, 8, active = false, flag = true), columns[0])
        assertEquals(Scratch(0, -1, active = true, flag = false), columns[1])
        assertEquals(Scratch(0, -1, active = true, flag = false), columns[2])
    }

    @Test
    fun `resize shrinks by moving the size marker and regrows with defaults`() {
        val columns = ScratchColumns(4)
        columns.resize(4)
        columns.updateAll { it.copy(value = 42, link = 42, active = false) }
        columns.resize(2)
        assertEquals(2, columns.size)
        columns.resize(3)
        assertEquals(Scratch(0, -1, active = true, flag = false), columns[2])
    }

    @Test
    fun `resize on empty columns starts from zero`() {
        val columns = ScratchColumns()
        columns.resize(2)
        assertEquals(Scratch(0, -1, active = true, flag = false), columns[0])
        assertEquals(Scratch(0, -1, active = true, flag = false), columns[1])
    }

    @Test
    fun `packed resize fills default slot words`() {
        val packed = ScratchPacked(1)
        packed.add(Scratch(7, 8, active = false, flag = true))
        packed.resize(3)
        assertEquals(Scratch(7, 8, active = false, flag = true), packed[0])
        assertEquals(Scratch(0, -1, active = true, flag = false), packed[1])
        assertEquals(Scratch(0, -1, active = true, flag = false), packed[2])
    }

    @Test
    fun `packed resize regrows with defaults after shrink`() {
        val packed = ScratchPacked(2)
        packed.resize(2)
        packed.updateAll { it.copy(link = 42, active = false) }
        packed.resize(1)
        packed.resize(2)
        assertEquals(Scratch(0, -1, active = true, flag = false), packed[1])
    }

    @Test
    fun `resize rejects negative size`() {
        assertFailsWith<IllegalArgumentException> { ScratchColumns().resize(-1) }
        assertFailsWith<IllegalArgumentException> { ScratchPacked().resize(-1) }
    }
}
