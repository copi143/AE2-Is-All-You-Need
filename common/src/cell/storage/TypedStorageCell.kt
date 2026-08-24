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
abstract class TypedStorageCell(size: Long, val label: String, override val keyType: AEKeyType) :
    ICellItem(size, "$label Storage Cell", "$label Cell") {

    /** Drive-cell block model id (texture + model), e.g. `16k_item_cell`. */
    val driveCellId: ResourceLocation get() = itemId2

    /** Housing returned on disassembly (AE2 parity: iron for items, copper for fluids). */
    val housingItem get() = CellHousings.of(keyType)

    /** Component returned on disassembly (1K..256T). Null for creative (-1). */
    val componentItem get() = if (isCreative) null else StorageComponents.ofSize(size)?.asItem()

    /**
     * Whether this cell's max amount (`size * amountPerByte`) overflows `Long`.
     * `true` → use BigInteger inventory, `false` → use fast long inventory.
     */
    val requiresBigInt: Boolean by lazy {
        if (isCreative) false else {
            val cap = java.math.BigInteger.valueOf(size)
                .multiply(java.math.BigInteger.valueOf(keyType.amountPerByte.toLong()))
            cap > java.math.BigInteger.valueOf(Long.MAX_VALUE)
        }
    }

    override val define: ItemDefinition<StorageCellItem> = ItemDefinition(
        itemName,
        itemId,
        StorageCellItem(Item.Properties().stacksTo(1), this),
    ).apply {
        MainCreativeTab.add(this)
    }
}
