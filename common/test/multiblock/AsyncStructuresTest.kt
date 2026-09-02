package allyouneed.multiblock

import allyouneed.multiblock.async.AsyncBlockKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncStructuresTest {

    @Test
    fun `module cells are never dont-care`() {
        val type = AsyncStructureType.MODULE
        for (y in 0 until AsyncStructures.height(type)) {
            for (z in 0 until AsyncStructures.depth(type, 0)) {
                for (x in 0 until AsyncStructures.width(type)) {
                    assertFalse(AsyncStructures.isDontCare(type, x, y, z))
                }
            }
        }
    }

    @Test
    fun `switch extension bays above floor are dont-care`() {
        val type = AsyncStructureType.SWITCH
        assertTrue(AsyncStructures.isDontCare(type, 4, 3, 12))
        assertTrue(AsyncStructures.isDontCare(type, 9, 4, 20))
        assertFalse(AsyncStructures.isDontCare(type, 9, 0, 12))
        assertFalse(AsyncStructures.isDontCare(type, 9, 4, 5))
    }

    @Test
    fun `processor hollow core layer is required air not dont-care`() {
        val type = AsyncStructureType.PROCESSOR
        assertFalse(AsyncStructures.isDontCare(type, 6, 8, 9))
        assertNull(AsyncStructures.blockAt(type, 0, 6, 8, 9))
        assertTrue(AsyncStructures.inCore(type, 6, 8, 9))
    }

    @Test
    fun `floor cells reject glass replacement`() {
        val type = AsyncStructureType.SWITCH
        assertTrue(AsyncStructures.isFloorCell(type, 5, 0, 5))
        assertTrue(AsyncStructures.isFloorCell(type, 5, 1, 5))
        assertEquals(AsyncBlockKind.MACHINE, AsyncStructures.blockAt(type, 0, 5, 0, 5))
        assertFalse(
            AsyncStructures.isValidCell(type, 0, 5, 0, 5, AsyncBlockKind.GLASS),
        )
        assertTrue(
            AsyncStructures.isValidCell(type, 0, 9, 4, 7, AsyncBlockKind.GLASS),
        )
    }

    @Test
    fun `switch core accepts wan and lan connectors`() {
        val type = AsyncStructureType.SWITCH
        assertEquals(AsyncBlockKind.MACHINE, AsyncStructures.blockAt(type, 0, 9, 4, 7))
        assertTrue(AsyncStructures.inCore(type, 9, 4, 7))
        assertTrue(
            AsyncStructures.isValidCell(type, 0, 9, 4, 7, AsyncBlockKind.WAN_CONNECTOR),
        )
        assertTrue(
            AsyncStructures.isValidCell(type, 0, 9, 4, 7, AsyncBlockKind.LAN_CONNECTOR),
        )
        assertFalse(
            AsyncStructures.isValidCell(type, 0, 9, 4, 7, AsyncBlockKind.ME_CONNECTOR),
        )
    }

    @Test
    fun `processor outer shell accepts me and lan connectors`() {
        val type = AsyncStructureType.PROCESSOR
        val shell = findProcessorShellMachine()
        val x = shell[0]
        val y = shell[1]
        val z = shell[2]
        assertTrue(AsyncStructures.isOuterShellCell(x, y, z))
        assertTrue(AsyncStructures.isValidCell(type, 0, x, y, z, AsyncBlockKind.ME_CONNECTOR))
        assertTrue(AsyncStructures.isValidCell(type, 0, x, y, z, AsyncBlockKind.LAN_CONNECTOR))
        assertFalse(AsyncStructures.isValidCell(type, 0, x, y, z, AsyncBlockKind.WAN_CONNECTOR))
    }

    @Test
    fun `module interface cell sits below the factory`() {
        assertEquals(Triple(1, -1, 2), AsyncStructures.moduleInterfaceCell)
        val (ax, ay, az) = AsyncStructures.anchorCell(AsyncStructureType.MODULE)
        assertEquals(1, ax)
        assertEquals(3, ay)
        assertEquals(0, az)
        assertEquals(AsyncBlockKind.FACTORY, AsyncStructures.blockAt(AsyncStructureType.MODULE, 0, ax, ay, az))
    }

    @Test
    fun `depths grow by six per extension`() {
        assertEquals(11, AsyncStructures.depth(AsyncStructureType.SWITCH, 0))
        assertEquals(17, AsyncStructures.depth(AsyncStructureType.SWITCH, 1))
        assertEquals(11 + 6 * 16, AsyncStructures.depth(AsyncStructureType.SWITCH, AsyncStructures.MAX_EXTENSIONS))
        assertEquals(19, AsyncStructures.depth(AsyncStructureType.PROCESSOR, 0))
        assertEquals(25, AsyncStructures.depth(AsyncStructureType.PROCESSOR, 1))
    }

    private fun findProcessorShellMachine(): IntArray {
        val type = AsyncStructureType.PROCESSOR
        for (y in 2..14) {
            for (z in 3..15) {
                for (x in 3..15) {
                    if (AsyncStructures.blockAt(type, 0, x, y, z) == AsyncBlockKind.MACHINE &&
                        AsyncStructures.isOuterShellCell(x, y, z)
                    ) {
                        return intArrayOf(x, y, z)
                    }
                }
            }
        }
        error("no processor shell machine cell")
    }
}
