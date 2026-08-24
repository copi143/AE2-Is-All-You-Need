package allyouneed.cell.item

import allyouneed.cell.storage.TypedStorageCell
import appeng.api.stacks.AEKeyType
import net.minecraft.resources.ResourceLocation

/**
 * Item storage cells from 1K to 256T, following the vanilla
 * `item_storage_cell` design but with long-based capacity.
 */
class ItemStorageCell(size: Long = -1) : TypedStorageCell(size, "Item Storage Cell", "Item Cell", AEKeyType.items()) {
    companion object {
        val entries = sizeList.map { ItemStorageCell(it) }
    }
}
