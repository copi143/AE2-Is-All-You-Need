package allyouneed.cell.mana

import allyouneed.cell.ICellItem
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/**
 * Mana storage cells from 1K to 256T, following the `item_storage_cell` design.
 * Each byte holds [allyouneed.logic.aekey.ManaKey.MANA_PER_BYTE] AM (the AE2 mana unit);
 * every mana system is stored as its own key (e.g. Botania mana at 1 Mana = 5 AM).
 */
class ManaStorageCell(size: Long = -1) : ICellItem(size, "Mana Storage Cell", "Mana Cell") {
    /** Drive-cell block model id (texture + model), matching vanilla `1k_item_cell`. */
    val driveCellId: ResourceLocation get() = itemId2

    override val define: ItemDefinition<ManaStorageCellItem> = ItemDefinition(
        itemName,
        itemId,
        ManaStorageCellItem(Item.Properties().stacksTo(1), this),
    ).apply {
        MainCreativeTab.add(this)
    }

    companion object {
        val entries = sizeList.map { ManaStorageCell(it) }
    }
}
