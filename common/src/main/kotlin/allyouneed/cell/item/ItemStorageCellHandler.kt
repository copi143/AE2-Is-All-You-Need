package allyouneed.cell.item

import appeng.api.stacks.GenericStack
import appeng.api.storage.cells.ICellHandler
import appeng.api.storage.cells.ISaveProvider
import appeng.api.storage.cells.StorageCell
import appeng.core.AEConfig
import appeng.items.storage.StorageCellTooltipComponent
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import java.util.Optional

object ItemStorageCellHandler : ICellHandler {
    override fun isCell(stack: ItemStack): Boolean =
        !stack.isEmpty && stack.item is ItemStorageCellItem

    override fun getCellInventory(stack: ItemStack, container: ISaveProvider?): StorageCell? {
        if (!stack.isEmpty && stack.item is ItemStorageCellItem) {
            return ItemStorageCellInventory(stack, container)
        }
        return null
    }

    fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> {
        val inv = getCellInventory(stack, null) as? ItemStorageCellInventory ?: return Optional.empty()

        val upgrades: List<ItemStack> = if (AEConfig.instance().isTooltipShowCellUpgrades) {
            inv.getUpgradesInventory().toList()
        } else {
            emptyList()
        }

        val content: List<GenericStack>
        val hasMoreContent: Boolean
        if (AEConfig.instance().isTooltipShowCellContent) {
            val maxCountShown = AEConfig.instance().tooltipMaxCellContentShown
            val all = inv.getCellItems().map { (key, amount) -> GenericStack(key, amount) }
            hasMoreContent = all.size > maxCountShown
            content = if (all.size > maxCountShown) all.subList(0, maxCountShown) else all
        } else {
            hasMoreContent = false
            content = emptyList()
        }

        return Optional.of(
            StorageCellTooltipComponent(
                upgrades,
                content,
                hasMoreContent,
                inv.isPreformatted(),
            ),
        )
    }
}
