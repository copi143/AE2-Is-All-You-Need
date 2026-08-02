package allyouneed.multiblock

import allyouneed.util.logger
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.ByteArrayTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/**
 * Loads [MultiblockPattern] definitions from the `data/ae2isallyouneed/multiblock` datapack
 * folder. NBT schema:
 * ```
 * {
 *   offset: [I; ox, oy, oz],                 // anchor (host) cell in pattern coordinates
 *   blocks: [{id: "mod:block"}, ...],        // any block, index is the id used in layers
 *   layers: [ [ [B; ...], ... ], ... ]       // layers[z][y] = ByteArrayTag along x
 * }
 * ```
 */
object MultiblockPatternLoader {

    fun loadFromResource(manager: ResourceManager, name: String): MultiblockPattern? {
        val id = ResourceLocation("ae2isallyouneed", "multiblock/$name.nbt")
        val resource = manager.getResource(id).orElse(null)
        if (resource == null) {
            logger.warn("Multiblock pattern {} not found", id)
            return null
        }
        return try {
            resource.open().use { stream ->
                fromNbt(NbtIo.readCompressed(stream))
            }
        } catch (e: Exception) {
            logger.error("Failed to load multiblock pattern {}", id, e)
            null
        }
    }

    fun fromNbt(tag: CompoundTag): MultiblockPattern? {
        val offset = tag.getIntArray("offset")
        if (offset.size != 3) {
            logger.error("Multiblock pattern offset must be an int array of length 3")
            return null
        }

        val blocksTag = tag.getList("blocks", Tag.TAG_COMPOUND.toInt())
        if (blocksTag.isEmpty) {
            logger.error("Multiblock pattern has no blocks")
            return null
        }
        val blocks = ArrayList<Block>(blocksTag.size)
        for (i in 0 until blocksTag.size) {
            val idStr = blocksTag.getCompound(i).getString("id")
            val block = ResourceLocation.tryParse(idStr)
                ?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
            if (block == null) {
                logger.error("Unknown block {} in multiblock pattern", idStr)
                return null
            }
            blocks.add(block)
        }

        val layersTag = tag.getList("layers", Tag.TAG_LIST.toInt())
        if (layersTag.isEmpty) {
            logger.error("Multiblock pattern has no layers")
            return null
        }
        val depth = layersTag.size
        val layers = arrayOfNulls<Array<ByteArray>>(depth)
        var height = -1
        var width = -1
        var nonAirCells = 0
        for (z in 0 until depth) {
            val zTag = layersTag.getList(z)
            if (zTag.isEmpty) {
                logger.error("Multiblock pattern layer {} is empty", z)
                return null
            }
            if (height == -1) height = zTag.size else if (zTag.size != height) {
                logger.error("Multiblock pattern layers have inconsistent height")
                return null
            }
            val row = arrayOfNulls<ByteArray>(height)
            for (y in 0 until height) {
                val bytes = zTag.get(y) as? ByteArrayTag
                    ?: run {
                        logger.error("Multiblock pattern layer row [{}, {}] is not a byte array", z, y)
                        return null
                    }
                val arr = bytes.getAsByteArray()
                if (width == -1) width = arr.size else if (arr.size != width) {
                    logger.error("Multiblock pattern layers have inconsistent width")
                    return null
                }
                for (x in arr.indices) {
                    val idx = arr[x].toInt()
                    if (idx in blocks.indices && blocks[idx] != Blocks.AIR) {
                        nonAirCells++
                    }
                }
                row[y] = arr
            }
            layers[z] = row.requireNoNulls()
        }
        if (width <= 0 || height <= 0) {
            logger.error("Multiblock pattern has empty dimensions")
            return null
        }

        val ox = offset[0]
        val oy = offset[1]
        val oz = offset[2]
        if (ox !in 0 until width || oy !in 0 until height || oz !in 0 until depth) {
            logger.error("Multiblock pattern offset {} is outside the layers", offset)
            return null
        }
        if (nonAirCells == 0) {
            logger.error("Multiblock pattern has no non-air cells")
            return null
        }

        return MultiblockPattern(
            offset = BlockPos(ox, oy, oz),
            blocks = blocks,
            layers = layers.requireNoNulls(),
        )
    }
}
