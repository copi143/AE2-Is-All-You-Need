package allyouneed.cell.item

import allyouneed.util.*
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/**
 * Item storage cells from 1K to 256T, following the vanilla
 * `item_storage_cell` design but with long-based capacity.
 */
enum class ItemStorageCell(size: Double = -1.0) {
    K1(1.0.Ki), //
    K4(4.0.Ki), //
    K16(16.0.Ki), //
    K64(64.0.Ki), //
    K256(256.0.Ki), //
    M1(1.0.Mi), //
    M4(4.0.Mi), //
    M16(16.0.Mi), //
    M64(64.0.Mi), //
    M256(256.0.Mi), //
    G1(1.0.Gi), //
    G4(4.0.Gi), //
    G16(16.0.Gi), //
    G64(64.0.Gi), //
    G256(256.0.Gi), //
    T1(1.0.Ti), //
    T4(4.0.Ti), //
    T16(16.0.Ti), //
    T64(64.0.Ti), //
    T256(256.0.Ti), //
    ;

    private val prefix = run {
        assert(size.toBits() and 0x00fffff_ffffffff == 0L)
        formatScaledUnit(size.floatingExp)
    }

    val itemName: String = prefix.uppercase() + " Item Storage Cell"

    /** Capacity in bytes for this tier (exact power-of-two, vanilla unit: KiB * 1024). */
    val sizeBytes: Long = size.toLong()

    /** Bytes reserved per distinct item type. AE2 scales this with tier: 8 bytes per KB. */
    val bytesPerType: Long = sizeBytes / 1024 * 8

    /** Total item capacity: 8 items per byte. */
    val maxItems: Long = sizeBytes * 8

    /** Idle energy drain in AE/t, scaling 0.5 per 4x tier like vanilla. */
    val idleDrain: Double = 0.5 + 0.5 * ((size.floatingExp - 10) / 2)

    val itemId: ResourceLocation = (prefix + "_item_storage_cell").rl

    /** Drive-cell block model id (texture + model), matching vanilla `1k_item_cell`. */
    val driveCellId: ResourceLocation = (prefix + "_item_cell").rl

    val item: ItemDefinition<ItemStorageCellItem> = ItemDefinition(
        itemName,
        itemId,
        ItemStorageCellItem(Item.Properties().stacksTo(1), this),
    ).apply {
        MainCreativeTab.add(this)
    }
}
