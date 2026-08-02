package allyouneed.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/**
 * A data-driven multiblock structure definition loaded from NBT.
 *
 * Pattern coordinates: x = 0..width-1 (west to east), y = 0..height-1 (bottom to top),
 * z = 0..depth-1 (back to front). The block at [offset] is the anchor (host) cell; once placed in
 * the world its blockstate facing determines the structure orientation.
 */
class MultiblockPattern(
    val offset: BlockPos,
    val blocks: List<Block>,
    private val layers: Array<Array<ByteArray>>,
) {
    val depth: Int = layers.size
    val height: Int = if (depth > 0) layers[0].size else 0
    val width: Int = if (height > 0) layers[0][0].size else 0

    /**
     * The pattern block at a cell, or null when the cell is a hole (air) or out of bounds.
     * Detection skips null cells.
     */
    fun blockAt(x: Int, y: Int, z: Int): Block? {
        if (z < 0 || z >= depth || y < 0 || y >= height || x < 0 || x >= width) {
            return null
        }
        val index = layers[z][y][x].toInt()
        if (index < 0 || index >= blocks.size) {
            return null
        }
        val block = blocks[index]
        return if (block == Blocks.AIR) null else block
    }

    fun isAirAt(x: Int, y: Int, z: Int): Boolean = blockAt(x, y, z) == null

    /** World offset of a pattern cell relative to the anchor (offset) cell for a horizontal facing. */
    fun worldOffset(facing: Direction, x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
        val right = facing.getClockWise()
        return Triple(
            (x - offset.x) * right.stepX + (z - offset.z) * facing.stepX,
            y - offset.y,
            (x - offset.x) * right.stepZ + (z - offset.z) * facing.stepZ,
        )
    }

    fun isHorizontalFacing(facing: Direction): Boolean = facing.axis.isHorizontal

    /** Raw block-index bytes for a single z/y row; exposed for (re)serialization. */
    fun layerBytes(z: Int, y: Int): ByteArray = layers[z][y]
}
