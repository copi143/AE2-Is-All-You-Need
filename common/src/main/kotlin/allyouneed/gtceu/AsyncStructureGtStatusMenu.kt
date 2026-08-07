package allyouneed.gtceu

import allyouneed.async.AsyncModuleCluster
import allyouneed.async.AsyncProcessorCluster
import allyouneed.async.AsyncSwitchCluster
import allyouneed.async.IAsyncCraftingStatusView
import appeng.menu.AEBaseMenu
import appeng.menu.guisync.GuiSync
import appeng.menu.implementations.MenuTypeBuilder
import com.gregtechceu.gtceu.api.machine.MetaMachine
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * GTCEu 形态的 async 合成结构控制器状态菜单。刻意只依赖 [BlockEntity]（从不依赖
 * GTCEu 方块实体类），因此可以由 AE2 的跨平台菜单机制构建。与普通状态菜单共用
 * 同一个界面。
 *
 * Status menu for the GTCEu controllers of the async synthesis structures. Deliberately only relies
 * on [BlockEntity] (never on the GTCEu block entity class) so it can be built by AE2's
 * cross-platform menu machinery. Shares the common screen with the plain status menu.
 */
class AsyncStructureGtStatusMenu(
    id: Int,
    playerInventory: Inventory,
    private val host: BlockEntity,
) : AEBaseMenu(TYPE, id, playerInventory, host), IAsyncCraftingStatusView {

    @GuiSync(1)
    override var formed: Int = 0
        private set

    @GuiSync(2)
    override var gridConnected: Int = 0
        private set

    @GuiSync(3)
    override var swallowedChannels: Int = 0
        private set

    @GuiSync(4)
    override var storageBytes: Long = 0
        private set

    @GuiSync(5)
    override var blockCount: Int = 0
        private set

    @GuiSync(6)
    override var infiniteChannelMode: Int = 0
        private set

    init {
        refreshStatus()
    }

    private fun refreshStatus() {
        val machine = MetaMachine.getMachine(host.level, host.blockPos) as? AsyncStructureGtControllerMachine
        val cluster = machine?.getCluster()
        formed = if (cluster != null) 1 else 0
        val views = machine?.getConnectorViews().orEmpty()
        gridConnected = if (views.any { it.isGridConnected }) 1 else 0
        swallowedChannels = views.sumOf { it.swallowedChannels }
        infiniteChannelMode = if (views.any { it.isInfiniteChannelMode }) 1 else 0
        storageBytes = (cluster as? AsyncProcessorCluster)?.storageBytes ?: 0
        blockCount = when (cluster) {
            is AsyncProcessorCluster -> cluster.getTotalBlockCount()
            is AsyncSwitchCluster -> cluster.blockCount
            is AsyncModuleCluster -> cluster.blockCount
            else -> 0
        }
    }

    override fun broadcastChanges() {
        refreshStatus()
        super.broadcastChanges()
    }

    companion object {
        val TYPE: MenuType<AsyncStructureGtStatusMenu> = MenuTypeBuilder
            .create(::AsyncStructureGtStatusMenu, BlockEntity::class.java)
            .build("async_crafting_status_gt")
    }
}
