package allyouneed

import allyouneed.cell.EnergyCell
import allyouneed.cell.CraftingStorage
import allyouneed.cell.storage.AllStorageCells
import allyouneed.cell.storage.CellHousings
import allyouneed.cell.storage.StorageComponents
import allyouneed.item.packet.AllPackets
import allyouneed.pattern.ModItems
import allyouneed.util.interfaces.NeedRegisterBlockEntity
import allyouneed.util.rl
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import appeng.core.definitions.ItemDefinition
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object AllRegistries {
    val blocks: ArrayList<BlockDefinition<out Block>> = arrayListOf()
    val items: ArrayList<ItemDefinition<out Item>> = arrayListOf()
    val blockEntityTypes: ArrayList<BlockEntityType<*>> = arrayListOf()
    val needRegisterBlockEntity :  ArrayList<NeedRegisterBlockEntity> = arrayListOf()

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
            needRegisterBlockEntity += it
        }
        CraftingStorage.entries.forEach {
            blocks += it.define
            needRegisterBlockEntity += it
        }
        AllStorageCells.entries.forEach {
            items += it.define
        }
        StorageComponents.entries.forEach { items += it }
        CellHousings.all.forEach { items += it }

        // Appear in AE2's main creative tab (same path as EnergyCell)
        MainCreativeTab.add(CREATIVE_ME_CELL)
        MainCreativeTab.add(DIMENSIONAL_CELL)
        items += CREATIVE_ME_CELL
        items += DIMENSIONAL_CELL

        // Register packet items
        AllPackets.init()
        AllPackets.all.forEach { items += it }
    }
}
