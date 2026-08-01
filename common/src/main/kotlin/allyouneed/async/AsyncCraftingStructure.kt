package allyouneed.async

import net.minecraft.core.Direction

/**
 * The fixed 3x3x4 arrangement of the async processing structure.
 *
 * Pattern coordinates: x = 0..2 (west to east), y = 0..2 (bottom to top), z = 0..3 (back to front).
 * The host block faces the +z direction (towards the glass window).
 */
object AsyncCraftingStructure {

    const val WIDTH = 3
    const val HEIGHT = 3
    const val DEPTH = 4

    // Layer strings are ordered bottom -> top, each character is west -> east.
    private val LAYERS: Array<Array<String>> = arrayOf(
        arrayOf("FWF", "WCW", "FWF"), // z = 0: back face (connector)
        arrayOf("WSW", "SSS", "WSW"), // z = 1
        arrayOf("WSW", "SHS", "WSW"), // z = 2 (host)
        arrayOf("FWF", "WGW", "FWF"), // z = 3: front face (glass window)
    )

    /** Pattern coordinate of the host. */
    private const val HOST_X = 1
    private const val HOST_Y = 1
    private const val HOST_Z = 2

    fun roleAt(x: Int, y: Int, z: Int): AsyncCraftingUnitRole = when (LAYERS[z][y][x]) {
        'H' -> AsyncCraftingUnitRole.HOST
        'C' -> AsyncCraftingUnitRole.CONNECTOR
        'S' -> AsyncCraftingUnitRole.STORAGE
        'G' -> AsyncCraftingUnitRole.GLASS
        else -> AsyncCraftingUnitRole.WALL
    }

    /** World offset of a pattern cell relative to the host block, for a given horizontal host facing. */
    fun worldOffset(facing: Direction, x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
        val right = facing.getClockWise()
        val dx = (x - HOST_X) * right.stepX + (z - HOST_Z) * facing.stepX
        val dy = y - HOST_Y
        val dz = (x - HOST_X) * right.stepZ + (z - HOST_Z) * facing.stepZ
        return Triple(dx, dy, dz)
    }

    /** Whether the given direction is a valid horizontal host facing. */
    fun isHorizontalFacing(facing: Direction): Boolean = facing.axis.isHorizontal
}
