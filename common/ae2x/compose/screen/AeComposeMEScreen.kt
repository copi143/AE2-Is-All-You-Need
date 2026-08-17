package ae2x.compose.screen

import ae2x.compose.AeComposeScreen
import appeng.api.config.Setting
import appeng.api.config.Settings
import appeng.api.config.SortDir
import appeng.api.config.SortOrder
import appeng.api.config.TypeFilter
import appeng.api.config.ViewItems
import appeng.api.util.IConfigManager
import appeng.client.gui.me.common.Repo
import appeng.client.gui.widgets.IScrollSource
import appeng.client.gui.widgets.ISortSource
import appeng.core.AEConfig
import appeng.helpers.InventoryAction
import appeng.integration.abstraction.ItemListMod
import appeng.menu.me.common.GridInventoryEntry
import appeng.menu.me.common.MEStorageMenu
import appeng.util.IConfigManagerListener
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ClickType
import org.lwjgl.glfw.GLFW

abstract class AeComposeMEScreen<M : MEStorageMenu>(
    menu: M,
    playerInventory: Inventory,
    title: Component,
) : AeComposeScreen<M>(menu, playerInventory, title),
    ISortSource,
    IScrollSource,
    IConfigManagerListener {

    val repo: Repo = Repo(this, this)
    var scrollRow: Int = 0
    var columns: Int = 9
    var searchText: String = ""
        private set

    init {
        menu.setClientRepo(repo)
        menu.setGui(this)
        repo.setRowSize(columns)
        val config = AEConfig.instance()
        if ((menu.isReturnedFromSubScreen || config.isRememberLastSearch) && rememberedSearch.isNotEmpty()) {
            applySearch(rememberedSearch, syncExternal = false)
        }
        if (!menu.isReturnedFromSubScreen && config.isUseExternalSearch && config.isClearExternalSearchOnOpen) {
            ItemListMod.setSearchText("")
        }
    }

    override fun getCurrentScroll(): Int = scrollRow

    override fun getSortBy(): SortOrder = menu.configManager.getSetting(Settings.SORT_BY)

    override fun getSortDir(): SortDir = menu.configManager.getSetting(Settings.SORT_DIRECTION)

    override fun getSortDisplay(): ViewItems = menu.configManager.getSetting(Settings.VIEW_MODE)

    override fun getTypeFilter(): TypeFilter = menu.configManager.getSetting(Settings.TYPE_FILTER)

    override fun onSettingChanged(manager: IConfigManager, setting: Setting<*>) {
        repo.updateView()
    }

    override fun updateBeforeRender() {
        super.updateBeforeRender()
        repo.setPaused(hasShiftDown())
        syncExternalSearch()
    }

    fun setSearch(text: String) {
        applySearch(text, syncExternal = true)
    }

    private fun applySearch(text: String, syncExternal: Boolean) {
        if (searchText == text && repo.searchString == text) return
        searchText = text
        repo.searchString = text
        repo.updateView()
        rememberedSearch = text
        val config = AEConfig.instance()
        if (syncExternal && !config.isUseExternalSearch && config.isSyncWithExternalSearch) {
            ItemListMod.setSearchText(text)
        }
    }

    private fun syncExternalSearch() {
        val config = AEConfig.instance()
        if (config.isUseExternalSearch) {
            val external = ItemListMod.getSearchText()
            if (external != repo.searchString) applySearch(external, syncExternal = false)
            return
        }
        if (config.isSyncWithExternalSearch && ItemListMod.hasSearchFocus()) {
            val external = ItemListMod.getSearchText()
            if (external != searchText) applySearch(external, syncExternal = false)
        }
    }

    fun handleRepoClick(entry: GridInventoryEntry?, button: Int, clickType: ClickType) {
        val window = Minecraft.getInstance().window.window
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS && entry != null) {
            menu.handleInteraction(entry.serial, InventoryAction.MOVE_REGION)
            return
        }
        if (entry == null) {
            if (clickType == ClickType.PICKUP && !menu.carried.isEmpty) {
                val action = if (button == 1) InventoryAction.SPLIT_OR_PLACE_SINGLE else InventoryAction.PICKUP_OR_SET_DOWN
                menu.handleInteraction(-1, action)
            }
            return
        }
        val serial = entry.serial
        val action = when (clickType) {
            ClickType.QUICK_MOVE -> if (button == 1) InventoryAction.PICKUP_SINGLE else InventoryAction.SHIFT_CLICK
            ClickType.CLONE -> if (entry.isCraftable) {
                menu.handleInteraction(serial, InventoryAction.AUTO_CRAFT)
                return
            } else if (menu.player.abilities.instabuild) {
                InventoryAction.CREATIVE_DUPLICATE
            } else {
                null
            }
            else -> {
                val pickup = if (button == 1) InventoryAction.SPLIT_OR_PLACE_SINGLE else InventoryAction.PICKUP_OR_SET_DOWN
                if (pickup == InventoryAction.PICKUP_OR_SET_DOWN &&
                    menu.carried.isEmpty &&
                    entry.storedAmount == 0L &&
                    entry.isCraftable
                ) {
                    menu.handleInteraction(serial, InventoryAction.AUTO_CRAFT)
                    return
                }
                pickup
            }
        }
        if (action != null) menu.handleInteraction(serial, action)
    }

    companion object {
        private var rememberedSearch: String = ""
    }
}
