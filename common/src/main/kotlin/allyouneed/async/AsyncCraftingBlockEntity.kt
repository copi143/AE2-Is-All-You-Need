package allyouneed.async

import appeng.blockentity.AEBaseBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * Base block entity for the non-grid blocks of the async processing structure
 * (host, storage, wall, glass).
 */
open class AsyncCraftingBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : AEBaseBlockEntity(type, pos, state), IAsyncCraftingBlockEntity {

    private val calc = AsyncCraftingCPUCalculator(this)
    private var cluster: AsyncCraftingCPUCluster? = null

    override val unitType: AsyncCraftingUnitType
        get() = (level?.getBlockState(worldPosition)?.block as? AsyncCraftingBlock)?.unitType
            ?: AsyncCraftingUnitType.WALL

    override fun getCluster(): AsyncCraftingCPUCluster? = cluster

    override fun isValid() = true

    override fun disconnect(update: Boolean) {
        if (cluster != null) {
            cluster?.destroy()
            if (update) {
                updateSubType(true)
            }
        }
    }

    override fun updateStatus(c: AsyncCraftingCPUCluster?) {
        if (cluster != null && cluster != c) {
            cluster?.destroy()
        }
        cluster = c
        updateSubType(true)
    }

    override fun updateMultiBlock(changedPos: BlockPos) {
        if (level is ServerLevel) {
            calc.updateMultiblockAfterNeighborUpdate(level as ServerLevel, worldPosition, changedPos)
        }
    }

    override fun onReady() {
        super.onReady()
        if (level is ServerLevel) {
            calc.calculateMultiblock(level as ServerLevel, worldPosition)
        }
    }

    open fun updateSubType(updateFormed: Boolean) {
        val lvl = level
        if (lvl == null || notLoaded() || isRemoved()) {
            return
        }
        val formed = isFormed()
        val current = lvl.getBlockState(worldPosition)
        if (current.block is AsyncCraftingBlock) {
            val newState = current.setValue(AsyncCraftingBlock.FORMED, formed)
            if (current != newState) {
                lvl.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS)
            }
        }
        if (updateFormed) {
            updateGridConnectableSides()
        }
    }

    open fun updateGridConnectableSides() {
        // Non-grid blocks have no grid connection.
    }

    open fun isFormed(): Boolean {
        if (isClientSide()) {
            return getBlockState().getValue(AsyncCraftingBlock.FORMED)
        }
        return cluster != null
    }

    open fun getPowered(): Boolean = false
}
