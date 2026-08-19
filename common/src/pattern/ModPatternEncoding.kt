package allyouneed.pattern

import appeng.api.stacks.GenericStack
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object ModPatternEncoding {

    fun encodePseudoPattern(
        displayName: Component?, icon: ItemStack?, inputs: Array<GenericStack?>
    ): ItemStack {
        val item = ModItems.PSEUDO_PATTERN
        return item.encode(displayName, icon, inputs)
    }
}
