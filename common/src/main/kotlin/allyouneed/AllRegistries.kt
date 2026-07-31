package allyouneed

import allyouneed.cell.CraftingStorage
import allyouneed.cell.EnergyCell
import allyouneed.cell.ItemStorageCell
import allyouneed.pattern.ModItems
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import appeng.core.definitions.ItemDefinition
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object AllRegistries {
    var blocks: Array<BlockDefinition<Block>> = arrayOf()
    var items: Array<ItemDefinition<Item>> = arrayOf()
    var blockEntityTypes: Array<BlockEntityType<*>> = arrayOf()

    val CREATIVE_ME_CELL: ItemDefinition<Item> = ItemDefinition(
        "Creative ME Storage Cell",
        "creative_me_cell".rl,
        ModItems.CREATIVE_ME_CELL,
    )

    val DIMENSIONAL_CELL: ItemDefinition<Item> = ItemDefinition(
        "Dimensional Storage Cell",
        "dimensional_cell".rl,
        ModItems.DIMENSIONAL_CELL,
    )

    init {
        EnergyCell.entries.forEach {
            blocks += it.define
        }
        CraftingStorage.entries.forEach {
            blocks += it.define
        }
        ItemStorageCell.entries.forEach {
            items += it.item as ItemDefinition<Item>
        }

        // Appear in AE2's main creative tab (same path as EnergyCell)
        MainCreativeTab.add(CREATIVE_ME_CELL)
        MainCreativeTab.add(DIMENSIONAL_CELL)
        items += CREATIVE_ME_CELL
        items += DIMENSIONAL_CELL
    }
}
