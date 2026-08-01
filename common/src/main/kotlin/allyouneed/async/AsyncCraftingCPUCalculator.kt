package allyouneed.async

import appeng.me.cluster.MBCalculator
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity

class AsyncCraftingCPUCalculator(t: IAsyncCraftingBlockEntity) :
    MBCalculator<IAsyncCraftingBlockEntity, AsyncCraftingCPUCluster>(t) {

    override fun checkMultiblockScale(min: BlockPos, max: BlockPos): Boolean {
        return max.x - min.x + 1 == AsyncCraftingStructure.WIDTH &&
            max.y - min.y + 1 == AsyncCraftingStructure.HEIGHT &&
            max.z - min.z + 1 == AsyncCraftingStructure.DEPTH
    }

    override fun createCluster(level: ServerLevel, min: BlockPos, max: BlockPos): AsyncCraftingCPUCluster {
        return AsyncCraftingCPUCluster(min, max)
    }

    override fun verifyInternalStructure(level: ServerLevel, min: BlockPos, max: BlockPos): Boolean {
        var candidateHost: BlockPos? = null
        for (p in BlockPos.betweenClosed(min, max)) {
            val be = level.getBlockEntity(p)
            if (be is IAsyncCraftingBlockEntity && be.unitType.role == AsyncCraftingUnitRole.HOST) {
                candidateHost = p
                break
            }
        }
        val hostPos = candidateHost ?: return false

        val hostState = level.getBlockState(hostPos)
        val hostBlock = hostState.block as? AsyncCraftingBlock ?: return false
        val hostFacing = hostBlock.getOrientationStrategy().getFacing(hostState)
        if (!AsyncCraftingStructure.isHorizontalFacing(hostFacing)) {
            return false
        }

        // The greedy bounding box must exactly match the pattern extent around the host.
        val right = hostFacing.getClockWise()
        val expectedMin = hostPos.offset(
            -right.stepX - 2 * hostFacing.stepX,
            -1,
            -right.stepZ - 2 * hostFacing.stepZ,
        )
        val expectedMax = hostPos.offset(
            right.stepX + hostFacing.stepX,
            1,
            right.stepZ + hostFacing.stepZ,
        )
        if (!expectedMin.equals(min) || !expectedMax.equals(max)) {
            return false
        }

        var connectorFound = false
        var storageFound = false
        for (lz in 0 until AsyncCraftingStructure.DEPTH) {
            for (ly in 0 until AsyncCraftingStructure.HEIGHT) {
                for (lx in 0 until AsyncCraftingStructure.WIDTH) {
                    val (dx, dy, dz) = AsyncCraftingStructure.worldOffset(hostFacing, lx, ly, lz)
                    val pos = hostPos.offset(dx, dy, dz)
                    val state = level.getBlockState(pos)
                    val block = state.block
                    if (block !is AsyncCraftingBlock) {
                        return false
                    }
                    val role = block.unitType.role
                    if (role != AsyncCraftingStructure.roleAt(lx, ly, lz)) {
                        return false
                    }
                    val be = level.getBlockEntity(pos)
                    if (be !is IAsyncCraftingBlockEntity) {
                        return false
                    }
                    when (role) {
                        AsyncCraftingUnitRole.CONNECTOR -> {
                            connectorFound = true
                            val connectorFacing = block.getOrientationStrategy().getFacing(state)
                            if (connectorFacing != hostFacing.opposite) {
                                return false
                            }
                        }
                        AsyncCraftingUnitRole.STORAGE -> storageFound = true
                        else -> {}
                    }
                }
            }
        }
        return connectorFound && storageFound
    }

    override fun updateBlockEntities(
        c: AsyncCraftingCPUCluster,
        level: ServerLevel,
        min: BlockPos,
        max: BlockPos,
    ) {
        for (p in BlockPos.betweenClosed(min, max)) {
            val be = level.getBlockEntity(p) as? IAsyncCraftingBlockEntity ?: continue
            be.updateStatus(c)
            c.addBlockEntity(be)
        }
    }

    override fun isValidBlockEntity(te: BlockEntity?): Boolean = te is IAsyncCraftingBlockEntity
}
