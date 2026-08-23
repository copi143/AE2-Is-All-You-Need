package allyouneed.cell.item

import allyouneed.cell.ICellItem
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/**
 * Item storage cells from 1K to 256T, following the vanilla
 * `item_storage_cell` design but with long-based capacity.
 */
class ItemStorageCell(size: Long = -1) : ICellItem(size, "Item Storage Cell", "Item Cell") {
    /** Drive-cell block model id (texture + model), matching vanilla `1k_item_cell`. */
    val driveCellId: ResourceLocation get() = itemId2

    override val define: ItemDefinition<ItemStorageCellItem> = ItemDefinition(
        itemName,
        itemId,
        ItemStorageCellItem(Item.Properties().stacksTo(1), this),
    ).apply {
        MainCreativeTab.add(this)
    }

    companion object {
        val entries = sizeList.map { ItemStorageCell(it) }
    }
}
