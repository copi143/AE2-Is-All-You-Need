package allyouneed.cell.dimensional

import appeng.api.config.FuzzyMode
import appeng.api.storage.StorageCells
import appeng.api.storage.cells.ICellWorkbenchItem
import appeng.api.upgrades.IUpgradeInventory
import appeng.api.upgrades.UpgradeInventories
import appeng.core.definitions.AEItems
import appeng.core.localization.GuiText
import appeng.core.localization.Tooltips
import appeng.items.AEBaseItem
import appeng.items.contents.CellConfig
import appeng.util.ConfigInventory
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * Dimensional storage cell: all AEKey types, BigInteger amounts, world-file backed.
 * Item NBT only stores a 24-bit cell id plus partition/upgrade config.
 */
class DimensionalCellItem(properties: Properties) : AEBaseItem(properties), ICellWorkbenchItem {

    override fun getConfigInventory(stack: ItemStack): ConfigInventory =
        CellConfig.create(stack)

    override fun getUpgrades(stack: ItemStack): IUpgradeInventory =
        UpgradeInventories.forItem(stack, 2)

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
        val inv = StorageCells.getCellInventory(stack, null) as? DimensionalCellInventory
        val cellId = getCellId(stack)
        if (cellId != 0) {
            lines.add(Tooltips.of(Component.literal("ID: #%06X".format(cellId))))
        } else {
            lines.add(Tooltips.of(Component.literal("Unbound")))
        }
        if (inv != null) {
            lines.add(Tooltips.of(Component.literal("Types: ${inv.getTypeCount()}")))
            if (inv.isPreformatted()) {
                val modeText = if (inv.getUpgradesInventory().isInstalled(AEItems.INVERTER_CARD)) {
                    GuiText.Excluded.text()
                } else {
                    GuiText.Included.text()
                }
                var line = GuiText.Partitioned.withSuffix(" - ").append(modeText)
                if (inv.isFuzzy()) {
                    line = line.append(" ").append(GuiText.Fuzzy.text())
                }
                lines.add(Tooltips.of(line))
            }
        }
    }

    companion object {
        private const val TAG_CELL_ID = "cid"
        private const val TAG_FUZZY = "FuzzyMode"

        fun create(): DimensionalCellItem =
            DimensionalCellItem(Item.Properties().stacksTo(1))

        fun getCellId(stack: ItemStack): Int {
            if (!stack.hasTag()) return 0
            return stack.tag!!.getInt(TAG_CELL_ID) and DimensionalCellStore.MAX_CELL_ID
        }

        fun setCellId(stack: ItemStack, id: Int) {
            stack.orCreateTag.putInt(TAG_CELL_ID, id and DimensionalCellStore.MAX_CELL_ID)
        }
    }
}
