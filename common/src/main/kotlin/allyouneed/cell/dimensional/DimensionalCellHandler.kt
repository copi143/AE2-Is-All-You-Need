package allyouneed.cell.dimensional

import appeng.api.storage.cells.ICellHandler
import appeng.api.storage.cells.ISaveProvider
import appeng.api.storage.cells.StorageCell
import net.minecraft.world.item.ItemStack

object DimensionalCellHandler : ICellHandler {
    override fun isCell(stack: ItemStack): Boolean =
        !stack.isEmpty && stack.item is DimensionalCellItem

    override fun getCellInventory(stack: ItemStack, container: ISaveProvider?): StorageCell? {
        if (!isCell(stack)) return null
        return DimensionalCellInventory(stack, container)
    }
}
