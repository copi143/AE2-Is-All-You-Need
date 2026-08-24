package allyouneed.cell.storage

import allyouneed.cell.ICell
import allyouneed.util.rl
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import net.minecraft.world.item.Item

/**
 * ME Storage Components - one per tier (1K..256T, 20 tiers). Mirrors AE2's
 * CELL_COMPONENT_1K..256K but extrapolated to 256T. Used in cell recipes
 * (component + housing -> cell) and returned on cell disassembly when empty.
 *
 * Texture: composed from cell_component_bg.png + cell_component_fg.png per tier,
 * tinted via [allyouneed.resgen.AE2_COLORS] like storage cells.
 */
object StorageComponents {
    val entries: List<ItemDefinition<StorageComponentItem>> = ICell.sizeList.map { size ->
        val exp = size.countTrailingZeroBits()
        val tier = allyouneed.util.formatScaledUnit(exp)
        val name = "${tier.uppercase()} ME Storage Component"
        val id = "cell_component_${tier.lowercase()}"
        val def = ItemDefinition(
            name,
            id.rl,
            StorageComponentItem(Item.Properties(), size),
        ).apply { MainCreativeTab.add(this) }
        def
    }

    /** Lookup component by exact byte size, or null if not a standard tier. */
    fun ofBytes(bytes: Long): ItemDefinition<StorageComponentItem>? =
        entries.find { it.asItem() is StorageComponentItem && (it.asItem() as StorageComponentItem).bytes == bytes }

    fun ofSize(size: Long): ItemDefinition<StorageComponentItem>? = ofBytes(size)
}
