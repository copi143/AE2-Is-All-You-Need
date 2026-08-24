package allyouneed.cell.storage

import allyouneed.util.rl
import appeng.api.stacks.AEKeyType
import appeng.core.MainCreativeTab
import appeng.core.definitions.ItemDefinition
import net.minecraft.world.item.Item
import net.minecraft.world.level.ItemLike

/**
 * Cell housings, one per key type. Each housing's texture is
 * `<type>_storage_cell_bg.png + storage_cell_case_fg.png` (per user request).
 * AE2 parity: item = iron, fluid = copper; custom types reuse iron.
 */
object CellHousings {
    private fun housingDef(type: String, displayLabel: String): ItemDefinition<Item> =
        ItemDefinition(
            "ME $displayLabel Cell Housing",
            "${type}_cell_housing".rl,
            Item(Item.Properties()),
        ).apply { MainCreativeTab.add(this) }

    val ITEM_CELL_HOUSING: ItemDefinition<Item> = housingDef("item", "Item")
    val FLUID_CELL_HOUSING: ItemDefinition<Item> = housingDef("fluid", "Fluid")
    val MANA_CELL_HOUSING: ItemDefinition<Item> = housingDef("mana", "Mana")
    val ENERGY_CELL_HOUSING: ItemDefinition<Item> = housingDef("energy", "Energy")
    val HP_CELL_HOUSING: ItemDefinition<Item> = housingDef("hp", "HP")
    val STA_CELL_HOUSING: ItemDefinition<Item> = housingDef("sta", "STA")
    val XP_CELL_HOUSING: ItemDefinition<Item> = housingDef("xp", "XP")

    private val byType: Map<String, ItemDefinition<Item>> = linkedMapOf(
        "item" to ITEM_CELL_HOUSING,
        "fluid" to FLUID_CELL_HOUSING,
        "mana" to MANA_CELL_HOUSING,
        "energy" to ENERGY_CELL_HOUSING,
        "hp" to HP_CELL_HOUSING,
        "sta" to STA_CELL_HOUSING,
        "xp" to XP_CELL_HOUSING,
    )

    /** Housing item for a given key type. */
    fun of(keyType: AEKeyType): ItemLike {
        // Resolve by AEKeyType id (e.g. "item", "fluid", custom type ids)
        val id = keyType.id.path // e.g. "item", "f" for fluid? Use string matching
        // AEKeyType id is ResourceLocation like ae2:item / ae2:fluid / custom
        // Fallback to map by known types
        return when {
            keyType == AEKeyType.items() -> ITEM_CELL_HOUSING.asItem()
            keyType == AEKeyType.fluids() -> FLUID_CELL_HOUSING.asItem()
            keyType.id.toString().contains("mana", ignoreCase = true) -> MANA_CELL_HOUSING.asItem()
            keyType.id.toString().contains("energy", ignoreCase = true) -> ENERGY_CELL_HOUSING.asItem()
            keyType.id.toString().contains("hp", ignoreCase = true) -> HP_CELL_HOUSING.asItem()
            keyType.id.toString().contains("sta", ignoreCase = true) -> STA_CELL_HOUSING.asItem()
            keyType.id.toString().contains("xp", ignoreCase = true) -> XP_CELL_HOUSING.asItem()
            else -> byType[id] ?.asItem() ?: ITEM_CELL_HOUSING.asItem()
        }
    }

    /** Housing by storageCellGroups key (item/fluid/mana/...) */
    fun ofGroupKey(groupKey: String): ItemLike =
        byType[groupKey]?.asItem() ?: ITEM_CELL_HOUSING.asItem()

    val all: List<ItemDefinition<Item>> = byType.values.toList()
}
