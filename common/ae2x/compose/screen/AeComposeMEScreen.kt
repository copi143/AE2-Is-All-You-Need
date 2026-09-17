package ae2x.compose.screen

import ae2x.compose.AeComposeScreen
import ae2x.compose.AeSlotGeometry
import allyouneed.util.bigint.BigAmounts
import androidx.compose.runtime.mutableIntStateOf
import appeng.api.behaviors.ContainerItemStrategies
import appeng.api.config.Setting
import appeng.api.config.Settings
import appeng.api.config.SortDir
import appeng.api.config.SortOrder
import appeng.api.config.TypeFilter
import appeng.api.config.ViewItems
import appeng.api.util.IConfigManager
import appeng.client.gui.me.common.Repo
import appeng.client.gui.me.common.RepoSlot
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
import net.minecraft.world.inventory.Slot
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

abstract class AeComposeMEScreen<M : MEStorageMenu>(
    menu: M,
    playerInventory: Inventory,
    title: Component,
) : AeComposeScreen<M>(menu, playerInventory, title),
    ISortSource,
    IScrollSource,
    IConfigManagerListener {

    val repo: Repo = Repo(this, this)
    private val scrollRowState = mutableIntStateOf(0)
    var scrollRow: Int
        get() = scrollRowState.intValue
        set(value) {
            scrollRowState.intValue = value
        }
    var columns: Int = 9
    var visibleRows: Int = 6
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

    override fun init() {
        repo.setRowSize(columns)
        ensureRepoSlots()
        super.init()
    }

    override fun getCurrentScroll(): Int = scrollRow

    fun maxScrollRows(): Int {
        var totalRows = (repo.size() + columns - 1) / columns.coerceAtLeast(1)
        if (repo.hasPinnedRow()) totalRows++
        return (totalRows - visibleRows).coerceAtLeast(0)
    }

    fun scrollRepo(deltaRows: Int): Boolean {
        val max = maxScrollRows()
        val next = (scrollRow + deltaRows).coerceIn(0, max)
        if (next == scrollRow) return max > 0
        scrollRow = next
        return true
    }

    fun ensureRepoSlots() {
        val wanted = (visibleRows * columns).coerceAtLeast(0)
        val existing = menu.slots.count { it is RepoSlot }
        if (existing == wanted) return
        menu.slots.removeIf { it is RepoSlot }
        repeat(wanted) { index ->
            val slot = RepoSlot(repo, index, AeSlotGeometry.HIDDEN, AeSlotGeometry.HIDDEN)
            menu.slots.add(slot)
            hideSlot(slot)
        }
    }

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

    override fun slotClicked(slot: Slot?, slotIdx: Int, mouseButton: Int, clickType: ClickType) {
        if (slot is RepoSlot) {
            handleRepoClick(slot.entry, mouseButton, clickType)
            return
        }
        super.slotClicked(slot, slotIdx, mouseButton, clickType)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        if (hasControlDown()) return super.mouseScrolled(mouseX, mouseY, delta)
        val slot = findSlot(mouseX, mouseY)
        if (slot is RepoSlot && delta != 0.0) {
            if (hasShiftDown()) {
                val serial = slot.entry?.serial ?: -1L
                val direction = if (delta > 0) InventoryAction.ROLL_DOWN else InventoryAction.ROLL_UP
                repeat(abs(delta).toInt().coerceAtLeast(1)) {
                    menu.handleInteraction(serial, direction)
                }
                return true
            }
            scrollRepo(if (delta > 0) -1 else 1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, delta)
    }

    fun handleRepoClick(entry: GridInventoryEntry?, button: Int, clickType: ClickType) {
        val window = Minecraft.getInstance().window.window
        if (button == 1 && clickType == ClickType.PICKUP && !menu.carried.isEmpty) {
            val emptying = ContainerItemStrategies.getEmptyingAction(menu.carried)
            if (emptying != null && menu.isKeyVisible(emptying.what())) {
                menu.handleInteraction(-1, InventoryAction.EMPTY_ITEM)
                return
            }
        }
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
        val effectiveType =
            if (Minecraft.getInstance().options.keyPickItem.matchesMouse(button)) ClickType.CLONE else clickType
        val action = when (effectiveType) {
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
                    shouldCraftOnClick(entry)
                ) {
                    menu.handleInteraction(serial, InventoryAction.AUTO_CRAFT)
                    return
                }
                pickup
            }
        }
        if (action != null) menu.handleInteraction(serial, action)
    }

    private fun shouldCraftOnClick(entry: GridInventoryEntry): Boolean {
        if (getSortDisplay() == ViewItems.CRAFTABLE) return true
        return BigAmounts.getEntryAmount(entry).signum() == 0 && entry.isCraftable
    }

    companion object {
        private var rememberedSearch: String = ""
    }
}
