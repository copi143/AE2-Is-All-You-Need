package allyouneed.multiblock.async

import allyouneed.api.AsyncChannelNodeHolder
import appeng.api.networking.GridFlags
import appeng.api.networking.IGridMultiblock
import appeng.api.networking.IGridNode
import appeng.api.networking.IGridNodeListener
import appeng.api.networking.pathing.ChannelMode
import appeng.api.orientation.BlockOrientation
import appeng.blockentity.AEBaseBlockEntity
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
 * async 合成控制器（网络控制器、网络交换机、工厂）与模块接口（Z）的方块实体。
 * 控制器是其多方块结构的锚点；接口记录搭建在它上面的模块。这些都不是网格方块。
 *
 * Block entity of the async synthesis controllers (network controller, network switch, factory)
 * and of the module interface (Z). Controllers are the anchors of their multiblock structure;
 * the interface tracks which module is mounted on it. None of these are grid blocks.
 */
open class AsyncStructureBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : AEBaseBlockEntity(type, pos, state), IAsyncStructureHost {

    private val calc = AsyncStructureCalculator(this)

    override val kind: AsyncBlockKind
        get() = (level?.getBlockState(worldPosition)?.block as? AsyncStructureEntityBlock<*>)?.kind
            ?: AsyncBlockKind.MACHINE

    fun isFormed(): Boolean {
        if (isClientSide) {
            return blockState.getValue(AsyncStructureEntityBlock.FORMED)
        }
        return calc.isFormed()
    }

    // -- cluster accessors (used by the connector BE and status queries) --

    fun getModuleCluster(): AsyncModuleCluster? = calc.getModuleCluster()
    fun getSwitchCluster(): AsyncSwitchCluster? = calc.getSwitchCluster()
    fun getProcessorCluster(): AsyncProcessorCluster? = calc.getProcessorCluster()

    /** 已成形结构的连接器的网格接入视图，供状态菜单使用。 / Grid-connected views of the connectors of the formed structure, for the status menus. */
    fun getConnectorViews(): List<IAsyncChannelView> {
        val lvl = level as? ServerLevel ?: return emptyList()
        val positions = when (val processor = calc.getProcessorCluster()) {
            null -> when (val sw = calc.getSwitchCluster()) {
                null -> emptyList()
                else -> sw.connectorPositions
            }

            else -> processor.connectorPositions
        }
        return positions.mapNotNull { lvl.getBlockEntity(it) as? IAsyncChannelView }
    }

    /** 强制对本结构做一次完整重扫，用于上游通知。 / Forces a full rescan of this structure. Used for upstream notifications. */
    override fun requestRescan() {
        val lvl = level as? ServerLevel ?: return
        calc.requestRescan(lvl)
    }

    /** 设置 FORMED 方块状态，但不通知邻居（避免重扫循环）。 / Sets the FORMED block state without notifying neighbours (no rescan loop). */
    fun setFormedState(formed: Boolean) {
        val lvl = level ?: return
        if (lvl.isClientSide || notLoaded() || isRemoved) return
        val current = lvl.getBlockState(worldPosition)
        if (current.block !is AsyncStructureEntityBlock<*>) return
        val newState = current.setValue(AsyncStructureEntityBlock.FORMED, formed)
        if (current != newState) {
            lvl.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS)
        }
    }

    fun updateSubType() {
        setFormedState(calc.isFormed())
    }

    fun updateMultiBlock(changedPos: BlockPos) {
        if (level is ServerLevel) {
            calc.updateMultiblockAfterNeighborUpdate(level as ServerLevel, worldPosition, changedPos)
        }
    }

    fun disconnect() {
        val lvl = level as? ServerLevel ?: return
        calc.destroy(lvl)
    }

    override fun onReady() {
        super.onReady()
        if (level is ServerLevel) {
            calc.calculateMultiblock(level as ServerLevel, worldPosition)
        }
    }
}

/**
 * async 合成连接器（ME / WAN / LAN）的方块实体。已接入网格：一旦所属结构成形，
 * 其网格节点就会吞掉通道，上限为稠密（dense）容量。连接器由所属结构控制器的
 * 计算器链接到该控制器。
 *
 * Block entity of the async synthesis connectors (ME / WAN / LAN). Grid-connected: once its
 * structure forms, its grid node swallows channels up to the dense capacity. The connector is
 * linked to its host structure's controller by the controller's calculator.
 */
class AsyncStructureConnectorBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : AENetworkBlockEntity(type, pos, state), IAsyncChannelSink, IAsyncChannelView {

    private var hostController: AsyncStructureBlockEntity? = null

    init {
        mainNode.setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
            .addService(IGridMultiblock::class.java, IGridMultiblock { getMultiblockNodes() })
    }

    val kind: AsyncBlockKind
        get() = (level?.getBlockState(worldPosition)?.block as? AsyncStructureConnectorBlock)?.kind
            ?: AsyncBlockKind.ME_CONNECTOR

    /** 把此连接器链接（或传入 null 解链）到一个已成形的宿主结构。 / Links (or unlinks, with null) this connector to a formed host structure. */
    fun setHostController(controller: AsyncStructureBlockEntity?) {
        if (hostController === controller) return
        hostController = controller
        updateSubType()
    }

    override fun isFormed(): Boolean {
        if (isClientSide) {
            return blockState.getValue(AsyncStructureEntityBlock.FORMED)
        }
        return hostController?.isFormed() == true
    }

    override val isGridConnected: Boolean
        get() = mainNode.isOnline

    fun updateSubType() {
        val lvl = level
        if (lvl == null || lvl.isClientSide || notLoaded() || isRemoved) return
        val current = lvl.getBlockState(worldPosition)
        if (current.block !is AsyncStructureConnectorBlock) return
        val newState = current.setValue(AsyncStructureEntityBlock.FORMED, isFormed())
            .setValue(AsyncStructureEntityBlock.POWERED, getPowered())
        if (current != newState) {
            lvl.setBlock(worldPosition, newState, Block.UPDATE_CLIENTS)
        }
        updateGridConnectableSides()
    }

    fun updateGridConnectableSides() {
        onGridConnectableSidesChanged()
    }

    override fun onMainNodeStateChanged(reason: IGridNodeListener.State) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            updateSubType()
        }
    }

    override fun getGridConnectableSides(orientation: BlockOrientation): Set<Direction> {
        if (!isFormed()) {
            return emptySet()
        }
        val facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING)
        return setOf(facing)
    }

    fun getPowered(): Boolean {
        if (isClientSide) {
            return blockState.getValue(AsyncStructureEntityBlock.POWERED)
        }
        return mainNode.isOnline
    }

    private fun getMultiblockNodes(): Iterator<IGridNode> {
        val nodes = ArrayList<IGridNode>()
        val controller = hostController ?: return nodes.iterator()
        val positions = when (controller.kind) {
            AsyncBlockKind.SWITCH -> controller.getSwitchCluster()?.connectorPositions
            AsyncBlockKind.CONTROLLER -> controller.getProcessorCluster()?.connectorPositions
            else -> null
        }
        if (positions != null) {
            for (pos in positions) {
                val be = level?.getBlockEntity(pos) as? AsyncStructureConnectorBlockEntity ?: continue
                val node = be.gridNode
                if (node != null) {
                    nodes.add(node)
                }
            }
        }
        return nodes.iterator()
    }

    fun updateMultiBlock(changedPos: BlockPos) {
        val lvl = level as? ServerLevel ?: return
        val controller: IAsyncStructureHost? =
            hostController ?: AsyncStructureDetector.findHostController(lvl, worldPosition)
        controller?.requestRescan()
    }

    fun disconnect() {
        setHostController(null)
        val lvl = level as? ServerLevel ?: return
        AsyncStructureDetector.findHostController(lvl, worldPosition)?.requestRescan()
    }

    override fun onReady() {
        super.onReady()
        val lvl = level as? ServerLevel ?: return
        if (hostController == null) {
            AsyncStructureDetector.findHostController(lvl, worldPosition)?.requestRescan()
        }
    }

    // -- Channel sink --

    override fun getItemFromBlockEntity(): Item {
        val lvl = level ?: return Items.AIR
        return (lvl.getBlockState(worldPosition).block as? AsyncStructureConnectorBlock)?.asItem() ?: Items.AIR
    }

    override val swallowedChannels: Int
        get() {
            val node = gridNode
            return if (node is AsyncChannelNodeHolder) node.asyncSwallowedChannels else 0
        }

    /**
     * 无限通道模式下通道数没有上限，sink 无法吞掉任何东西；只要结构成形且已接入
     * 网格，就视为正常工作。
     *
     * In INFINITE channel mode channels are unbounded, so the sink cannot swallow anything;
     * the structure is treated as working as long as it is formed and connected.
     */
    override val isInfiniteChannelMode: Boolean
        get() = gridNode?.grid?.pathingService?.channelMode == ChannelMode.INFINITE
}
