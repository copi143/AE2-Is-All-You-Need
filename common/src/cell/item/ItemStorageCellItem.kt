package allyouneed.cell.item

import allyouneed.cell.ICellItem
import allyouneed.cell.appendPartitionInfo
import allyouneed.cell.getFuzzyMode
import allyouneed.cell.setFuzzyMode
import appeng.api.config.FuzzyMode
import appeng.api.storage.StorageCells
import appeng.api.storage.cells.CellState
import appeng.api.storage.cells.ICellWorkbenchItem
import appeng.api.upgrades.IUpgradeInventory
import appeng.api.upgrades.UpgradeInventories
import appeng.core.localization.GuiText
import appeng.core.localization.Tooltips
import appeng.items.AEBaseItem
import appeng.items.contents.CellConfig
import appeng.util.ConfigInventory
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import java.util.*

/**
 * Base item for resource storage cells (item / mana / ...). Holds the data-side [ICellItem]
 * definition and provides the shared cell-workbench plumbing (config, upgrades, fuzzy mode,
 * capacity tooltip). Concrete subclasses decide which [allyouneed.cell.ICell] they pair with.
 */
open class ResourceCellItem(
    properties: Properties,
    /** Data-side definition providing size tier, bytes per type and idle drain. */
    val cell: ICellItem,
) : AEBaseItem(properties), ICellWorkbenchItem {

    override fun getConfigInventory(stack: ItemStack): ConfigInventory = CellConfig.create(stack)

    override fun getUpgrades(stack: ItemStack): IUpgradeInventory =
        UpgradeInventories.forItem(stack, UPGRADE_SLOTS)

    override fun getFuzzyMode(stack: ItemStack): FuzzyMode = stack.getFuzzyMode()

    override fun setFuzzyMode(stack: ItemStack, fzMode: FuzzyMode) {
        stack.setFuzzyMode(fzMode)
    }

    override fun appendHoverText(
        stack: ItemStack,
        level: Level?,
        lines: MutableList<Component>,
        advancedTooltips: TooltipFlag,
    ) {
        val inv = StorageCells.getCellInventory(stack, null) as? ItemStorageCellInventory ?: return
        lines.add(Tooltips.bytesUsed(inv.getUsedBytes(), inv.getTotalBytes()))
        lines.add(Tooltips.typesUsed(inv.getStoredItemTypes(), inv.getTotalItemTypes()))
        if (inv.isPreformatted()) {
            val line = GuiText.Partitioned.withSuffix(" - ").appendPartitionInfo(
                inv.getPartitionListMode(),
                inv.isFuzzy(),
            )
            lines.add(Tooltips.of(line))
        }
    }

    companion object {
        const val UPGRADE_SLOTS = 4

        /** Item tint: layer1 is the status LED, matching vanilla storage cells. */
        fun getColor(stack: ItemStack, tintIndex: Int): Int {
            if (tintIndex == 1) {
                val inv = StorageCells.getCellInventory(stack, null)
                val state = inv?.status ?: CellState.EMPTY
                return state.stateColor
            }
            return 0xFFFFFF
        }
    }
}

/**
 * Item storage cell (1K - 256T). Long-based capacity following the vanilla
 * `item_storage_cell` design; NBT storage with `keys`/`amts` tags.
 */
open class ItemStorageCellItem(
    properties: Properties,
    cellType: ItemStorageCell,
) : ResourceCellItem(properties, cellType) {

    override fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> =
        ItemStorageCellHandler.getTooltipImage(stack)
}
