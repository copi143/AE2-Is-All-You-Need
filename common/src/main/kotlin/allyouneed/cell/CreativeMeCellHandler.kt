package allyouneed.cell

import appeng.api.stacks.GenericStack
import appeng.api.storage.cells.ICellHandler
import appeng.api.storage.cells.ISaveProvider
import appeng.api.storage.cells.StorageCell
import appeng.core.AEConfig
import appeng.items.contents.CellConfig
import appeng.items.storage.StorageCellTooltipComponent
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.ItemStack
import java.util.Optional

object CreativeMeCellHandler : ICellHandler {
    override fun isCell(stack: ItemStack): Boolean =
        !stack.isEmpty && stack.item is CreativeMeCellItem

    override fun getCellInventory(stack: ItemStack, container: ISaveProvider?): StorageCell? {
        if (!stack.isEmpty && stack.item is CreativeMeCellItem) {
            return CreativeMeCellInventory(stack)
        }
        return null
    }

    fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> {
        if (getCellInventory(stack, null) == null) {
            return Optional.empty()
        }

        val cc = CellConfig.create(stack)
        val content: List<GenericStack>
        val hasMoreContent: Boolean

        if (AEConfig.instance().isTooltipShowCellContent) {
            val maxCountShown = AEConfig.instance().tooltipMaxCellContentShown
            val all = cc.keySet().map { GenericStack(it, 1) }
            hasMoreContent = all.size > maxCountShown
            content = if (all.size > maxCountShown) all.subList(0, maxCountShown) else all
        } else {
            hasMoreContent = false
            content = emptyList()
        }

        return Optional.of(
            StorageCellTooltipComponent(
                emptyList(),
                content,
                hasMoreContent,
                false,
            ),
        )
    }
}
