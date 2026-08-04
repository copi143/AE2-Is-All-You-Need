package allyouneed.async

import appeng.menu.AEBaseMenu
import appeng.menu.guisync.GuiSync
import appeng.menu.implementations.MenuTypeBuilder
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType

/**
 * 自有方块形态的普通 async 合成控制器状态菜单。读取控制器 [AsyncStructureBlockEntity]
 * 缓存的结构簇以及其连接器的网格状态。与 GTCEu 状态菜单共用同一个界面。
 *
 * Status menu for the plain async synthesis controllers (own-block flavour). Reads the structure
 * cluster cached by the controller's [AsyncStructureBlockEntity] and the grid state of its
 * connectors. Shares the common screen with the GTCEu status menu.
 */
class AsyncCraftingStatusMenu(
    id: Int,
    playerInventory: Inventory,
    private val host: AsyncStructureBlockEntity,
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
        val processor = host.getProcessorCluster()
        val sw = host.getSwitchCluster()
        val module = host.getModuleCluster()
        val cluster = processor ?: sw ?: module
        formed = if (cluster != null) 1 else 0
        val views = host.getConnectorViews()
        gridConnected = if (views.any { it.isGridConnected }) 1 else 0
        swallowedChannels = views.sumOf { it.swallowedChannels }
        infiniteChannelMode = if (views.any { it.isInfiniteChannelMode }) 1 else 0
        storageBytes = processor?.storageBytes ?: 0
        blockCount = when {
            processor != null -> processor.getTotalBlockCount()
            sw != null -> sw.blockCount
            module != null -> module.blockCount
            else -> 0
        }
    }

    override fun broadcastChanges() {
        refreshStatus()
        super.broadcastChanges()
    }

    companion object {
        val TYPE: MenuType<AsyncCraftingStatusMenu> = MenuTypeBuilder
            .create(::AsyncCraftingStatusMenu, AsyncStructureBlockEntity::class.java)
            .build("async_crafting_status")
    }
}
