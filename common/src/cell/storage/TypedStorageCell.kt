package allyouneed.cell.storage

import allyouneed.cell.ICellItem
import appeng.api.stacks.AEKeyType
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/**
 * Shared data-side definition for resource storage cells (item / fluid / mana / energy / ...).
 * Subclasses only pick the display labels and the [keyType]; the drive-cell model id follows
 * the vanilla `<tier>_<type>_cell` naming (e.g. `16k_mana_cell`) and the definition is added
 * to AE2's main creative tab.
 */
abstract class TypedStorageCell(
    size: Long,
    label: String,
    driveLabel: String,
    override val keyType: AEKeyType,
) : ICellItem(size, label, driveLabel) {

    /** Drive-cell block model id (texture + model), e.g. `16k_item_cell`. */
    val driveCellId: ResourceLocation get() = itemId2

    override val define: ItemDefinition<StorageCellItem> = ItemDefinition(
        itemName,
        itemId,
        StorageCellItem(Item.Properties().stacksTo(1), this),
    ).apply {
        MainCreativeTab.add(this)
    }
}
