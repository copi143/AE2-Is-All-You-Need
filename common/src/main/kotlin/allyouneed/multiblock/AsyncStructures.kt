package allyouneed.multiblock

import allyouneed.async.AsyncBlockKind
import net.minecraft.core.Direction

/**
 * Hand-written, data-driven-free definitions of the three async synthesis structures.
 *
 * Each structure lives in a local coordinate system and is anchored at its controller block:
 *
 *  - MODULE:  3 wide (x) x 7 high (y) x 5 deep (z). Factory at (1, 3, 0) on the front face.
 *  - SWITCH:  19 wide x 7 high x (11 + 6N) deep. Switch block at (9, 4, 3) on the core's front
 *             face. Base core is 13x5x5; the floor behind the core carries the extension bays.
 *  - PROCESSOR: 19 wide x 15 high x (19 + 6N) deep. Controller at (9, 8, 3) on the core's front
 *             face. Base core is 13x13x13.
 *
 * A cell is either required or "don't care". A required cell must contain the block returned by
 * [blockAt]; when [blockAt] returns null the cell must be air (e.g. the processor's 7x7 air
 * layer). Don't-care cells ([isDonCare]) accept anything. Coordinates grow x = west->east,
 * y = bottom->top, z = front->back. The controller faces the front (increasing local z); the
 * structure body extends "behind" the controller.
 */
enum class AsyncStructureType(val baseDepth: Int) {
    MODULE(5), SWITCH(11), PROCESSOR(19),
}

object AsyncStructures {

    const val MAX_EXTENSIONS = 16
    const val EXTENSION_DEPTH = 6

    fun width(type: AsyncStructureType): Int = when (type) {
        AsyncStructureType.MODULE -> 3
        AsyncStructureType.SWITCH -> 19
        AsyncStructureType.PROCESSOR -> 19
    }

    fun height(type: AsyncStructureType): Int = when (type) {
        AsyncStructureType.MODULE -> 7
        AsyncStructureType.SWITCH -> 7
        AsyncStructureType.PROCESSOR -> 15
    }

    fun depth(type: AsyncStructureType, extensions: Int): Int = when (type) {
        AsyncStructureType.MODULE -> 5
        else -> type.baseDepth + EXTENSION_DEPTH * extensions
    }

    /** Anchor (controller) cell in local coordinates. */
    fun anchorCell(type: AsyncStructureType): Triple<Int, Int, Int> = when (type) {
        AsyncStructureType.MODULE -> Triple(1, 3, 0)
        AsyncStructureType.SWITCH -> Triple(9, 4, 3)
        AsyncStructureType.PROCESSOR -> Triple(9, 8, 3)
    }

    /**
     * World offset of a local cell relative to the anchor for a horizontal facing. Local +y is up,
     * local +z points along [facing] and local +x along the facing's clockwise side.
     */
    fun worldOffset(type: AsyncStructureType, facing: Direction, x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
        val (ax, ay, az) = anchorCell(type)
        val right = facing.clockWise
        return Triple(
            (x - ax) * right.stepX + (z - az) * facing.stepX,
            y - ay,
            (x - ax) * right.stepZ + (z - az) * facing.stepZ,
        )
    }

    /**
     * Whether a local cell may contain anything. All other in-bounds cells are required; see the
     * class comment for the distinction between required blocks and required air.
     */
    fun isDonCare(type: AsyncStructureType, x: Int, y: Int, z: Int): Boolean {
        if (x < 0 || y < 0 || z < 0) return false
        if (x >= width(type) || y >= height(type) || z >= depth(type, 0)) return false
        return when (type) {
            AsyncStructureType.MODULE -> false
            AsyncStructureType.SWITCH -> y in 2..<height(type) && !inCore(type, x, y, z)
            AsyncStructureType.PROCESSOR -> y in 2..<height(type) && !inCore(type, x, y, z)
        }
    }

    /**
     * Expected block kind at a local cell. Only meaningful for required cells: returns null for
     * required-air cells. Ignore it for don't-care cells.
     */
    fun blockAt(type: AsyncStructureType, extensions: Int, x: Int, y: Int, z: Int): AsyncBlockKind? {
        if (x < 0 || y < 0 || z < 0) return null
        if (x >= width(type) || y >= height(type) || z >= depth(type, extensions)) return null
        return when (type) {
            AsyncStructureType.MODULE -> moduleAt(x, y, z)
            AsyncStructureType.SWITCH -> switchAt(extensions, x, y, z)
            AsyncStructureType.PROCESSOR -> processorAt(extensions, x, y, z)
        }
    }

    /**
     * Whether a block of [actual] kind is acceptable at a local cell. Handles the replacement rules:
     * machine glass may replace machine blocks on walls, and core machine blocks may be replaced by
     * the matching connectors.
     */
    fun isValidCell(
        type: AsyncStructureType,
        extensions: Int,
        x: Int,
        y: Int,
        z: Int,
        actual: AsyncBlockKind
    ): Boolean {
        val expected = blockAt(type, extensions, x, y, z) ?: return true
        if (expected == actual) return true

        if (expected == AsyncBlockKind.MACHINE && actual == AsyncBlockKind.GLASS && !isFloorCell(type, x, y, z)) {
            return true
        }

        return when (type) {
            AsyncStructureType.MODULE -> false
            AsyncStructureType.SWITCH -> expected == AsyncBlockKind.MACHINE && inCore(type, x, y, z) && actual in setOf(
                AsyncBlockKind.WAN_CONNECTOR,
                AsyncBlockKind.LAN_CONNECTOR
            )

            AsyncStructureType.PROCESSOR -> expected == AsyncBlockKind.MACHINE && inCore(
                type,
                x,
                y,
                z
            ) && isOuterShellCell(x, y, z) && actual in setOf(AsyncBlockKind.ME_CONNECTOR, AsyncBlockKind.LAN_CONNECTOR)
        }
    }

    private fun isFloorCell(type: AsyncStructureType, x: Int, y: Int, z: Int): Boolean = when (type) {
        AsyncStructureType.MODULE -> y == 0 || y == height(type) - 1
        else -> y == 0 || y == 1
    }

    private fun inCore(type: AsyncStructureType, x: Int, y: Int, z: Int): Boolean {
        val b = coreBounds(type)
        return x in b[0]..b[1] && y in b[2]..b[3] && z in b[4]..b[5]
    }

    private fun coreBounds(type: AsyncStructureType): IntArray = when (type) {
        AsyncStructureType.SWITCH -> intArrayOf(3, 15, 2, 6, 3, 7)
        AsyncStructureType.PROCESSOR -> intArrayOf(3, 15, 2, 14, 3, 15)
        else -> throw IllegalStateException("no core")
    }

    private fun isOuterShellCell(x: Int, y: Int, z: Int): Boolean {
        val b = coreBounds(AsyncStructureType.PROCESSOR)
        var count = 0
        if (x == b[0] || x == b[1]) count++
        if (y == b[2] || y == b[3]) count++
        if (z == b[4] || z == b[5]) count++
        return count == 1
    }

    // ---------------------------------------------------------------------------------------------
    // MODULE (3 x 7 x 5)
    // ---------------------------------------------------------------------------------------------

    private fun moduleAt(x: Int, y: Int, z: Int): AsyncBlockKind? {
        if (y == 0 || y == height(AsyncStructureType.MODULE) - 1) {
            return when {
                x == 0 || x == 2 -> AsyncBlockKind.FRAME
                z == 0 || z == 4 -> AsyncBlockKind.FRAME
                else -> AsyncBlockKind.MACHINE
            }
        }
        if (z == 0) {
            return when {
                x == 0 || x == 2 -> AsyncBlockKind.FRAME
                y == 3 -> AsyncBlockKind.FACTORY
                else -> AsyncBlockKind.MACHINE
            }
        }
        if (z == 4) {
            return if (x == 0 || x == 2) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
        }
        if (x == 0 || x == 2) {
            return AsyncBlockKind.MACHINE
        }
        return when (z) {
            1, 3 -> AsyncBlockKind.TOWER
            2 -> if (y == 3) AsyncBlockKind.EXECUTION else AsyncBlockKind.ENERGY
            else -> null
        }
    }

    /** The module interface (Z) cell directly below the module's bottom centre. */
    val moduleInterfaceCell: Triple<Int, Int, Int> = Triple(1, -1, 2)

    // ---------------------------------------------------------------------------------------------
    // SWITCH (19 x 7 x (11 + 6N))
    // ---------------------------------------------------------------------------------------------

    private fun switchAt(extensions: Int, x: Int, y: Int, z: Int): AsyncBlockKind? {
        val d = depth(AsyncStructureType.SWITCH, extensions)
        if (y == 0) {
            return if (x == 0 || x == 18 || z == 0 || z == d - 1) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
        }
        if (y == 1) {
            return upperFloorCell(AsyncStructureType.SWITCH, extensions, x, z)
        }
        if (x in 3..15 && z in 3..7) {
            return switchCoreCell(x, y, z)
        }
        return null
    }

    private fun switchCoreCell(x: Int, y: Int, z: Int): AsyncBlockKind? {
        val edgeX = x == 3 || x == 15
        val edgeY = y == 2 || y == 6
        val edgeZ = z == 3 || z == 7
        var edges = 0
        if (edgeX) edges++
        if (edgeY) edges++
        if (edgeZ) edges++
        if (edges >= 2) return AsyncBlockKind.FRAME
        if (edges == 1) {
            return if (x == 9 && y == 4 && z == 3) AsyncBlockKind.SWITCH else AsyncBlockKind.MACHINE
        }
        return when (z) {
            4, 6 -> if (y == 4) AsyncBlockKind.ENERGY else AsyncBlockKind.TOWER
            5 -> if (y == 4) AsyncBlockKind.COMPUTING else AsyncBlockKind.ENERGY
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PROCESSOR (19 x 15 x (19 + 6N))
    // ---------------------------------------------------------------------------------------------

    private fun processorAt(extensions: Int, x: Int, y: Int, z: Int): AsyncBlockKind? {
        val d = depth(AsyncStructureType.PROCESSOR, extensions)
        if (y == 0) {
            return if (x == 0 || x == 18 || z == 0 || z == d - 1) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
        }
        if (y == 1) {
            return upperFloorCell(AsyncStructureType.PROCESSOR, extensions, x, z)
        }
        if (x in 3..15 && y in 2..14 && z in 3..15) {
            return processorCoreCell(x, y, z)
        }
        return null
    }

    private fun processorCoreCell(x: Int, y: Int, z: Int): AsyncBlockKind? {
        val lx = minOf(x - 3, 15 - x)
        val ly = minOf(y - 2, 14 - y)
        val lz = minOf(z - 3, 15 - z)
        val d = minOf(lx, ly, lz)

        val atLayer = fun(v: Int, layer: Int): Boolean = v == layer

        return when (d) {
            0 -> {
                var count = 0
                if (atLayer(lx, 0)) count++
                if (atLayer(ly, 0)) count++
                if (atLayer(lz, 0)) count++
                if (count >= 2) {
                    AsyncBlockKind.FRAME
                } else {
                    if (x == 9 && y == 8 && z == 3) AsyncBlockKind.CONTROLLER else AsyncBlockKind.MACHINE
                }
            }

            1 -> {
                var count = 0
                if (atLayer(lx, 1)) count++
                if (atLayer(ly, 1)) count++
                if (atLayer(lz, 1)) count++
                if (count == 3) {
                    AsyncBlockKind.FRAME
                } else if (count == 2) {
                    AsyncBlockKind.TOWER
                } else if (lx == 2 || ly == 2 || lz == 2) {
                    AsyncBlockKind.ENERGY
                } else {
                    null
                }
            }

            2 -> {
                var count = 0
                if (atLayer(lx, 2)) count++
                if (atLayer(ly, 2)) count++
                if (atLayer(lz, 2)) count++
                when (count) {
                    3 -> AsyncBlockKind.FRAME
                    2 -> AsyncBlockKind.TOWER
                    else -> null
                }
            }

            3 -> null
            4 -> AsyncBlockKind.COMPUTING
            else -> AsyncBlockKind.STORAGE
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared floor / bay logic
    // ---------------------------------------------------------------------------------------------

    /**
     * The upper floor at y = 1. A full rectangle with a frame ring and machine interior; the core
     * footprint is "don't care" (it is covered by the core). Extension bays are appended behind the
     * core, each providing two module interfaces (Z) at local x = 5 and x = 13.
     */
    private fun upperFloorCell(type: AsyncStructureType, extensions: Int, x: Int, z: Int): AsyncBlockKind? {
        val d = depth(type, extensions)
        val upperZ1 = 1
        val upperZ2 = d - 2
        if (x !in 1..17 || z < upperZ1 || z > upperZ2) return null
        if (x == 1 || x == 17 || z == upperZ1 || z == upperZ2) return AsyncBlockKind.FRAME

        val cz1 = 3
        val cz2 = if (type == AsyncStructureType.SWITCH) 7 else 15
        if (x in 3..15 && z in cz1..cz2) return null

        // The base closing B row (z = cz2 + 1) precedes the first bay, whose opening row is the
        // split B row (frame columns at x = 1, 9, 17) per the README floor layout.
        val bayStart = cz2 + 2
        if (z < bayStart) return AsyncBlockKind.MACHINE

        val inBay = (z - bayStart) / EXTENSION_DEPTH in 0 until extensions
        if (!inBay) return AsyncBlockKind.MACHINE

        val row = (z - bayStart) % EXTENSION_DEPTH
        return when (row) {
            0, 4 -> if (x == 1 || x == 9 || x == 17) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
            1, 3 -> when (x) {
                1, 9, 17 -> AsyncBlockKind.FRAME
                2, 8, 10, 16 -> AsyncBlockKind.MACHINE
                else -> null
            }

            2 -> when (x) {
                5, 13 -> AsyncBlockKind.MODULE_INTERFACE
                1, 9, 17 -> AsyncBlockKind.FRAME
                2, 8, 10, 16 -> AsyncBlockKind.MACHINE
                else -> null
            }

            5 -> if (x == 1 || x == 17) AsyncBlockKind.FRAME else AsyncBlockKind.TOWER
            else -> null
        }
    }
}
