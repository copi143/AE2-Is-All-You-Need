package allyouneed.async

import appeng.menu.AEBaseMenu
import appeng.menu.guisync.GuiSync
import appeng.menu.implementations.MenuTypeBuilder
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType

class AsyncCraftingStatusMenu(
    id: Int,
    playerInventory: Inventory,
    private val host: AsyncCraftingBlockEntity,
) : AEBaseMenu(TYPE, id, playerInventory, host) {

    @GuiSync(1)
    var formed: Int = 0
        private set

    @GuiSync(2)
    var gridConnected: Int = 0
        private set

    @GuiSync(3)
    var swallowedChannels: Int = 0
        private set

    @GuiSync(4)
    var storageBytes: Long = 0
        private set

    @GuiSync(5)
    var blockCount: Int = 0
        private set

    @GuiSync(6)
    var infiniteChannelMode: Int = 0
        private set

    init {
        refreshStatus()
    }

    private fun refreshStatus() {
        val cluster = host.getCluster()
        formed = if (cluster != null) 1 else 0
        gridConnected = if (cluster != null && cluster.isGridConnected()) 1 else 0
        swallowedChannels = cluster?.getSwallowedChannels() ?: 0
        storageBytes = cluster?.getStorageBytes() ?: 0
        blockCount = cluster?.getBlockCount() ?: 0
        infiniteChannelMode = if (cluster != null && cluster.isInfiniteChannelMode()) 1 else 0
    }

    override fun broadcastChanges() {
        refreshStatus()
        super.broadcastChanges()
    }

    companion object {
        val TYPE: MenuType<AsyncCraftingStatusMenu> = MenuTypeBuilder
            .create(::AsyncCraftingStatusMenu, AsyncCraftingBlockEntity::class.java)
            .build("async_crafting_status")
    }
}
