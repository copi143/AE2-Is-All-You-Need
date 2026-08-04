package allyouneed.cell

import appeng.api.config.FuzzyMode
import appeng.api.config.IncludeExclude
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
import java.util.Optional

/**
 * Item storage cell (1K - 256T). Long-based capacity following the vanilla
 * `item_storage_cell` design; NBT storage with `keys`/`amts` tags.
 */
class ItemStorageCellItem(
    properties: Properties,
    val cellType: ItemStorageCell,
) : AEBaseItem(properties), ICellWorkbenchItem {

    override fun getConfigInventory(stack: ItemStack): ConfigInventory = CellConfig.create(stack)

    override fun getUpgrades(stack: ItemStack): IUpgradeInventory =
        UpgradeInventories.forItem(stack, UPGRADE_SLOTS)

    override fun getFuzzyMode(stack: ItemStack): FuzzyMode {
        val fz = stack.orCreateTag.getString(TAG_FUZZY)
        if (fz.isEmpty()) return FuzzyMode.IGNORE_ALL
        return try {
            FuzzyMode.valueOf(fz)
        } catch (_: IllegalArgumentException) {
            FuzzyMode.IGNORE_ALL
        }
    }

    override fun setFuzzyMode(stack: ItemStack, fzMode: FuzzyMode) {
        stack.orCreateTag.putString(TAG_FUZZY, fzMode.name)
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
            val modeText = when (inv.getPartitionListMode()) {
                IncludeExclude.BLACKLIST -> GuiText.Excluded.text()
                else -> GuiText.Included.text()
            }
            var line = GuiText.Partitioned.withSuffix(" - ").append(modeText)
            if (inv.isFuzzy()) {
                line = line.append(" ").append(GuiText.Fuzzy.text())
            }
            lines.add(Tooltips.of(line))
        }
    }

    override fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> =
        ItemStorageCellHandler.getTooltipImage(stack)

    companion object {
        private const val TAG_FUZZY = "FuzzyMode"
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
