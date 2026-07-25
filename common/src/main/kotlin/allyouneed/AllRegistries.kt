package allyouneed

import allyouneed.energy.EnergyCell
import appeng.core.definitions.BlockDefinition
import appeng.core.definitions.ItemDefinition
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

object AllRegistries {
    var blocks: Array<BlockDefinition<Block>> = arrayOf()
    var items: Array<ItemDefinition<Item>> = arrayOf()
    var blockEntityTypes: Array<BlockEntityType<*>> = arrayOf()

    init {
        EnergyCell.entries.forEach {
            blocks += it.define
        }
    }
}
