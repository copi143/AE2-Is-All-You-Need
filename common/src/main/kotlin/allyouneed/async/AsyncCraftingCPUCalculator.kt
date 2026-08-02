package allyouneed.async

import allyouneed.multiblock.MultiblockEditor
import allyouneed.multiblock.MultiblockPattern
import allyouneed.multiblock.MultiblockPatterns
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel

/**
 * Anchor-based detector for the async processing structure.
 *
 * The structure is matched against the currently loaded data-driven [MultiblockPattern] by
 * comparing block registry ids (air cells are skipped). The anchor is the host block at the
 * pattern's offset cell; its blockstate facing determines the structure orientation.
 */
class AsyncCraftingCPUCalculator(anchor: IAsyncCraftingBlockEntity) {

    private var cluster: AsyncCraftingCPUCluster? = null

    fun calculateMultiblock(level: ServerLevel, pos: BlockPos) {
        revalidate(level, pos)
    }

    fun updateMultiblockAfterNeighborUpdate(level: ServerLevel, selfPos: BlockPos, changedPos: BlockPos) {
        revalidate(level, selfPos)
    }

    private fun revalidate(level: ServerLevel, triggerPos: BlockPos) {
        if (level.dimension() == MultiblockEditor.EDITOR_DIMENSION) {
            destroy()
            return
        }
        val pattern = MultiblockPatterns.async
        val hostPos = resolveHost(level, triggerPos, pattern)
        if (hostPos == null) {
            destroy()
            return
        }
        val hostState = level.getBlockState(hostPos)
        val hostBlock = hostState.block as? AsyncCraftingBlock ?: run {
            destroy()
            return
        }
        val hostFacing = hostBlock.orientationStrategy.getFacing(hostState)
        if (!pattern.isHorizontalFacing(hostFacing)) {
            destroy()
            return
        }

        var connectorFound = false
        var storageFound = false
        var connectorFacingOk = true
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        val found = ArrayList<IAsyncCraftingBlockEntity>()
        for (z in 0 until pattern.depth) {
            for (y in 0 until pattern.height) {
                for (x in 0 until pattern.width) {
                    val expected = pattern.blockAt(x, y, z) ?: continue
                    val (dx, dy, dz) = pattern.worldOffset(hostFacing, x, y, z)
                    val pos = hostPos.offset(dx, dy, dz)
                    val actual = level.getBlockState(pos).block
                    if (BuiltInRegistries.BLOCK.getKey(actual) != BuiltInRegistries.BLOCK.getKey(expected)) {
                        destroy()
                        return
                    }
                    minX = minOf(minX, pos.x)
                    minY = minOf(minY, pos.y)
                    minZ = minOf(minZ, pos.z)
                    maxX = maxOf(maxX, pos.x)
                    maxY = maxOf(maxY, pos.y)
                    maxZ = maxOf(maxZ, pos.z)

                    if (expected is AsyncCraftingBlock) {
                        val be = level.getBlockEntity(pos)
                        if (be !is IAsyncCraftingBlockEntity) {
                            destroy()
                            return
                        }
                        found.add(be)
                        when (expected.unitType.role) {
                            AsyncCraftingUnitRole.CONNECTOR -> {
                                connectorFound = true
                                val connectorFacing =
                                    expected.orientationStrategy.getFacing(level.getBlockState(pos))
                                if (connectorFacing != hostFacing.opposite) {
                                    connectorFacingOk = false
                                }
                            }
                            AsyncCraftingUnitRole.STORAGE -> storageFound = true
                            else -> {}
                        }
                    }
                }
            }
        }
        if (!connectorFound || !storageFound || !connectorFacingOk) {
            destroy()
            return
        }

        val c = AsyncCraftingCPUCluster(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ))
        for (be in found) {
            c.addBlockEntity(be)
        }
        form(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ), c)
    }

    /**
     * Locates the host (anchor) block. If the triggering block is the host itself it is used
     * directly; otherwise candidate host positions are probed using each horizontal facing and
     * the pattern's offset cell.
     */
    private fun resolveHost(level: ServerLevel, triggerPos: BlockPos, pattern: MultiblockPattern): BlockPos? {
        val triggerState = level.getBlockState(triggerPos)
        val triggerBlock = triggerState.block as? AsyncCraftingBlock
        if (triggerBlock?.unitType?.role == AsyncCraftingUnitRole.HOST) {
            val facing = triggerBlock.orientationStrategy.getFacing(triggerState)
            return if (pattern.isHorizontalFacing(facing)) triggerPos else null
        }
        for (facing in Direction.Plane.HORIZONTAL) {
            val (dx, dy, dz) = pattern.worldOffset(facing, pattern.offset.x, pattern.offset.y, pattern.offset.z)
            val hostPos = triggerPos.offset(-dx, -dy, -dz)
            val hostState = level.getBlockState(hostPos)
            val hostBlock = hostState.block as? AsyncCraftingBlock ?: continue
            if (hostBlock.unitType.role != AsyncCraftingUnitRole.HOST) {
                continue
            }
            if (hostBlock.getOrientationStrategy().getFacing(hostState) != facing) {
                continue
            }
            return hostPos
        }
        return null
    }

    private fun form(min: BlockPos, max: BlockPos, c: AsyncCraftingCPUCluster) {
        val existing = cluster
        if (existing != null && !existing.isDestroyed() &&
            existing.getBoundsMin() == min && existing.getBoundsMax() == max
        ) {
            return
        }
        destroy()
        cluster = c
        c.updateStatus(true)
    }

    private fun destroy() {
        val c = cluster ?: return
        cluster = null
        c.destroy()
    }
}
