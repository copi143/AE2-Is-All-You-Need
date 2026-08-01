package allyouneed.async

import appeng.me.cluster.IAECluster
import appeng.me.cluster.MBCalculator
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity

class AsyncCraftingCPUCluster(
    private val boundsMin: BlockPos,
    private val boundsMax: BlockPos,
) : IAECluster {

    private val blockEntities = ArrayList<IAsyncCraftingBlockEntity>()
    private var isDestroyed = false
    private var storage = 0L

    fun addBlockEntity(be: IAsyncCraftingBlockEntity) {
        blockEntities.add(be)
        if (be.unitType.role == AsyncCraftingUnitRole.STORAGE) {
            storage += be.unitType.storageBytes
        }
    }

    override fun getBoundsMin(): BlockPos = boundsMin

    override fun getBoundsMax(): BlockPos = boundsMax

    override fun isDestroyed(): Boolean = isDestroyed

    override fun updateStatus(updateGrid: Boolean) {
        for (be in blockEntities) {
            be.updateStatus(this)
        }
    }

    override fun destroy() {
        if (isDestroyed) {
            return
        }
        isDestroyed = true

        val ownsModification = !MBCalculator.isModificationInProgress()
        if (ownsModification) {
            MBCalculator.setModificationInProgress(this)
        }
        try {
            for (be in blockEntities) {
                be.updateStatus(null)
            }
        } finally {
            if (ownsModification) {
                MBCalculator.setModificationInProgress(null)
            }
        }
    }

    override fun getBlockEntities(): Iterator<BlockEntity> =
        blockEntities.filterIsInstance<BlockEntity>().iterator()

    fun getBlockEntityList(): List<IAsyncCraftingBlockEntity> = blockEntities

    fun getHost(): AsyncCraftingBlockEntity? =
        blockEntities.firstOrNull { it.unitType.role == AsyncCraftingUnitRole.HOST } as? AsyncCraftingBlockEntity

    fun getConnector(): AsyncCraftingConnectorBlockEntity? =
        blockEntities.filterIsInstance<AsyncCraftingConnectorBlockEntity>().firstOrNull()

    fun getStorageBytes(): Long = storage

    fun getBlockCount(): Int = blockEntities.size

    fun getSwallowedChannels(): Int = getConnector()?.swallowedChannels ?: 0

    fun isGridConnected(): Boolean = getConnector()?.isGridConnected ?: false

    fun isInfiniteChannelMode(): Boolean = getConnector()?.isInfiniteChannelMode ?: false
}
