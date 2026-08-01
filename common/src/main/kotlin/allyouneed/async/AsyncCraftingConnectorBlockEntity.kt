package allyouneed.async

import appeng.api.networking.GridFlags
import appeng.api.networking.IGridMultiblock
import appeng.api.networking.IGridNode
import appeng.api.networking.IGridNodeListener
import appeng.api.networking.pathing.ChannelMode
import appeng.api.orientation.BlockOrientation
import appeng.blockentity.grid.AENetworkBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

/**
 * The grid-connected block entity of the async processing structure. Its grid node swallows
 * all available channels (up to 32) once the structure is formed.
 */
class AsyncCraftingConnectorBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : AENetworkBlockEntity(type, pos, state),
    IAsyncCraftingBlockEntity,
    IAsyncChannelSink {

    private val calc = AsyncCraftingCPUCalculator(this)
    private var cluster: AsyncCraftingCPUCluster? = null

    init {
        getMainNode()
            .setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
            .addService(IGridMultiblock::class.java, IGridMultiblock { getMultiblockNodes() })
    }

    override val unitType: AsyncCraftingUnitType
        get() = (level?.getBlockState(worldPosition)?.block as? AsyncCraftingBlock)?.unitType
            ?: AsyncCraftingUnitType.CONNECTOR

    override fun getItemFromBlockEntity(): Item {
        val lvl = level
        if (lvl == null) {
            return Items.AIR
        }
        return (lvl.getBlockState(worldPosition).block as? AsyncCraftingBlock)?.asItem() ?: Items.AIR
    }

    // -- IAsyncCraftingBlockEntity --

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

    // -- Multiblock grid handling --

    override fun getGridConnectableSides(orientation: BlockOrientation): Set<Direction> {
        if (!isFormed()) {
            return emptySet()
        }
        val facing = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING)
        return setOf(facing)
    }

    fun updateSubType(updateFormed: Boolean) {
        val lvl = level
        if (lvl == null || notLoaded() || isRemoved()) {
            return
        }
        val formed = isFormed()
        val powered = getPowered()
        val current = lvl.getBlockState(worldPosition)
        if (current.block is AsyncCraftingBlock) {
            val newState = current.setValue(AsyncCraftingBlock.FORMED, formed)
                .setValue(AsyncCraftingBlock.POWERED, powered)
            if (current != newState) {
                lvl.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS)
            }
        }
        if (updateFormed) {
            updateGridConnectableSides()
        }
    }

    fun updateGridConnectableSides() {
        onGridConnectableSidesChanged()
    }

    override fun onMainNodeStateChanged(reason: IGridNodeListener.State) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            updateSubType(false)
        }
    }

    override fun isFormed(): Boolean {
        if (isClientSide()) {
            return getBlockState().getValue(AsyncCraftingBlock.FORMED)
        }
        return cluster != null
    }

    fun getPowered(): Boolean {
        if (isClientSide()) {
            return getBlockState().getValue(AsyncCraftingBlock.POWERED)
        }
        return mainNode.isOnline()
    }

    private fun getMultiblockNodes(): Iterator<IGridNode> {
        val nodes = ArrayList<IGridNode>()
        val c = cluster
        if (c != null) {
            for (be in c.getBlockEntityList()) {
                if (be is AsyncCraftingConnectorBlockEntity) {
                    val node = be.gridNode
                    if (node != null) {
                        nodes.add(node)
                    }
                }
            }
        }
        return nodes.iterator()
    }

    // -- Status query --

    val swallowedChannels: Int
        get() {
            val node = gridNode
            return if (node is AsyncChannelNodeHolder) node.getAsyncSwallowedChannels() else 0
        }

    val isGridConnected: Boolean
        get() = mainNode.isOnline()

    /**
     * In INFINITE channel mode channels are unbounded, so the sink cannot swallow anything;
     * the structure is treated as working as long as it is formed and connected.
     */
    val isInfiniteChannelMode: Boolean
        get() = gridNode?.grid?.pathingService?.channelMode == ChannelMode.INFINITE
}
