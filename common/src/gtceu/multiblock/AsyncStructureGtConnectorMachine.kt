package allyouneed.gtceu.multiblock

import allyouneed.api.AsyncChannelNodeHolder
import allyouneed.multiblock.async.AsyncStructureEntityBlock
import allyouneed.multiblock.async.IAsyncChannelSink
import allyouneed.multiblock.async.IAsyncChannelView
import appeng.api.networking.GridFlags
import appeng.api.networking.IGridMultiblock
import appeng.api.networking.IGridNode
import appeng.api.networking.IGridNodeListener
import appeng.api.networking.pathing.ChannelMode
import appeng.api.orientation.BlockOrientation
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder
import com.gregtechceu.gtceu.integration.ae2.utils.SerializableManagedGridNode
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import java.util.*

/**
 * async 合成系统的 GTCEu 连接器（ME / WAN / LAN）。通过 [GridNodeHolder] 风格的
 * trait 接入网格；只要宿主结构成形，节点就吞掉通道（上限 32，稠密容量）。连接器
 * 由所属结构控制器的检测器链接到该控制器。
 *
 * GTCEu connector (ME / WAN / LAN) of the async synthesis system. Grid-connected through a
 * [GridNodeHolder]-style trait; the node swallows channels (up to 32, the dense capacity) as long
 * as the host structure is formed. The connector is linked to its host structure's controller by
 * the controller's detector.
 */
abstract class AsyncStructureGtConnectorMachine(holder: IMachineBlockEntity) : MetaMachine(holder),
    IGridConnectedMachine, IAsyncChannelSink, IAsyncChannelView {

    private val gridNodeHolder: AsyncStructureGridNodeTrait = AsyncStructureGridNodeTrait(this)

    private var hostController: AsyncStructureGtControllerMachine? = null
    private var online = false

    override fun getMainNode(): SerializableManagedGridNode = gridNodeHolder.getMainNode()

    override fun isOnline(): Boolean = online

    override fun setOnline(online: Boolean) {
        this.online = online
    }

    /** 把此连接器链接（或传入 null 解链）到一个已成形的宿主结构。 / Links (or unlinks, with null) this connector to a formed host structure. */
    fun setHostController(controller: AsyncStructureGtControllerMachine?) {
        if (hostController === controller) return
        hostController = controller
        updateExposedSides()
        updateFormedState()
    }

    fun getHostController(): AsyncStructureGtControllerMachine? = hostController

    override fun onLoad() {
        super.onLoad()
        updateExposedSides()
        updateFormedState()
    }

    override fun onMainNodeStateChanged(reason: IGridNodeListener.State) {
        super.onMainNodeStateChanged(reason)
        updateMEStatus()
    }

    override fun getGridConnectableSides(orientation: BlockOrientation): Set<Direction> {
        return if (isFormed()) EnumSet.of(self().frontFacing) else emptySet()
    }

    /** 改变暴露面（成形 vs 未成形）会触发一次路径重算。 / Changing the exposed sides (formed vs unformed) triggers a pathing recalculation. */
    private fun updateExposedSides() {
        val sides = if (isFormed()) setOf(self().frontFacing) else emptySet<Direction>()
        gridNodeHolder.getMainNode().setExposedOnSides(sides)
    }

    /** 与普通 async 连接器一致：翻转 FORMED，让客户端显示成形模型。 / Mirrors the vanilla async connectors: flip FORMED so the client shows the formed model. */
    private fun updateFormedState() {
        val level = level as? ServerLevel ?: return
        val current = level.getBlockState(pos)
        if (current.block !is AsyncStructureGtMachineBlock) return
        val newState = current.setValue(AsyncStructureEntityBlock.FORMED, isFormed())
        if (current != newState) {
            level.setBlock(pos, newState, Block.UPDATE_CLIENTS)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Channel sink / view
    // ---------------------------------------------------------------------------------------------

    override fun isFormed(): Boolean = hostController?.isFormed() == true

    override val isGridConnected: Boolean
        get() = gridNodeHolder.getMainNode().isOnline

    override val swallowedChannels: Int
        get() = (gridNodeHolder.getMainNode().node as? AsyncChannelNodeHolder)?.asyncSwallowedChannels ?: 0

    override val isInfiniteChannelMode: Boolean
        get() = gridNodeHolder.getMainNode().node?.grid?.pathingService?.channelMode == ChannelMode.INFINITE
}

/** async 合成系统的 GTCEu ME 连接器。 / GTCEu ME connector of the async synthesis system. */
class AsyncStructureGtMeConnectorMachine(holder: IMachineBlockEntity) : AsyncStructureGtConnectorMachine(holder)

/** async 合成系统的 GTCEu WAN 连接器。 / GTCEu WAN connector of the async synthesis system. */
class AsyncStructureGtWanConnectorMachine(holder: IMachineBlockEntity) : AsyncStructureGtConnectorMachine(holder)

/** async 合成系统的 GTCEu LAN 连接器。 / GTCEu LAN connector of the async synthesis system. */
class AsyncStructureGtLanConnectorMachine(holder: IMachineBlockEntity) : AsyncStructureGtConnectorMachine(holder)

/**
 * 为 async 连接器配置的 [GridNodeHolder]：节点参与结构的多方块节点组（因此所有
 * 连接器共享一条通道），并通过通道吞噬 mixin 吞掉最多 32 条稠密通道。暴露面
 * 初始为空，由宿主结构的成形状态经 [AsyncStructureGtConnectorMachine.updateExposedSides]
 * 驱动。
 *
 * [GridNodeHolder] configured for the async connectors: the node participates in the structure's
 * multiblock node group (so all connectors share one channel) and swallows up to the dense 32
 * channels via the channel-swallow mixin. Exposed sides start empty and are driven by the host
 * structure's formed state through [AsyncStructureGtConnectorMachine.updateExposedSides].
 */
class AsyncStructureGridNodeTrait(
    connectedMachine: IGridConnectedMachine,
) : GridNodeHolder(connectedMachine) {

    override fun createManagedNode(): SerializableManagedGridNode {
        // setFlags/setExposedOnSides return the base ManagedGridNode type, so the cast is required
        // to keep the SerializableManagedGridNode (matches GridNodeHolder.createManagedNode).
        val node = super.createManagedNode()
            .setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
            .setExposedOnSides(emptySet()) as SerializableManagedGridNode
        node.addService(IGridMultiblock::class.java, IGridMultiblock { collectMultiblockNodes() })
        return node
    }

    /** 同一宿主结构的所有连接器节点组成一个多方块通道组。 / All connector nodes of the same host structure form one multiblock channel group. */
    private fun collectMultiblockNodes(): Iterator<IGridNode> {
        val nodes = ArrayList<IGridNode>()
        val connector = machine as? AsyncStructureGtConnectorMachine ?: return nodes.iterator()
        val host = connector.getHostController() ?: return nodes.iterator()
        val level = machine.level ?: return nodes.iterator()
        for (pos in host.connectorPositions()) {
            val node = (MetaMachine.getMachine(level, pos) as? AsyncStructureGtConnectorMachine)?.getMainNode()?.node
            if (node != null) {
                nodes.add(node)
            }
        }
        return nodes.iterator()
    }
}
