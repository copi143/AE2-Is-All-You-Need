package allyouneed

import allyouneed.energy.EnergyCell
import appeng.core.definitions.BlockDefinition
import appeng.core.definitions.ItemDefinition
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

object AllRegistries {
    var blocks: Array<BlockDefinition<Block>> = arrayOf()
    var items: Array<ItemDefinition<Item>> = arrayOf()

    init {
        EnergyCell.entries.forEach {
            blocks += it.define
        }
    }
}
