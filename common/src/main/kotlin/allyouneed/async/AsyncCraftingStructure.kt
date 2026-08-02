package allyouneed.async

import allyouneed.multiblock.MultiblockPattern
import allyouneed.rl
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/**
 * Fallback definition of the async processing structure, used when the data-driven NBT pattern
 * (`data/ae2isallyouneed/multiblock/async_crafting.nbt`) is missing or fails to load.
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

    private val charToUnit: Map<Char, AsyncCraftingUnitType> = mapOf(
        'H' to AsyncCraftingUnitType.HOST,
        'C' to AsyncCraftingUnitType.CONNECTOR,
        'S' to AsyncCraftingUnitType.STORAGE,
        'G' to AsyncCraftingUnitType.GLASS,
        'F' to AsyncCraftingUnitType.WALL,
        'W' to AsyncCraftingUnitType.WALL,
    )

    fun defaultPattern(): MultiblockPattern {
        val units = AsyncCraftingUnitType.entries
        val indexByUnit = units.withIndex().associate { (i, u) -> u to i }
        val blocks: List<Block> =
            units.map { u -> BuiltInRegistries.BLOCK.getOptional(u.id.rl).orElse(Blocks.AIR) }
        val layers = Array(DEPTH) { z ->
            Array(HEIGHT) { y ->
                ByteArray(WIDTH) { x ->
                    val unit = charToUnit[LAYERS[z][y][x]]!!
                    indexByUnit[unit]!!.toByte()
                }
            }
        }
        return MultiblockPattern(
            offset = BlockPos(1, 1, 2),
            blocks = blocks,
            layers = layers,
        )
    }
}
