package allyouneed.pattern.adaptive

import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.IPatternDetailsDecoder
import appeng.api.stacks.AEItemKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object AdaptivePatternDecoder : IPatternDetailsDecoder {
    override fun isEncodedPattern(stack: ItemStack): Boolean {
        return stack.item is AdaptivePatternItem
    }

    override fun decodePattern(stack: ItemStack, level: Level, tryRecovery: Boolean): IPatternDetails? {
        val item = stack.item
        if (item is AdaptivePatternItem) {
            return item.decode(stack, level, tryRecovery)
        }
        return null
    }

    override fun decodePattern(what: AEItemKey, level: Level): IPatternDetails? {
        val item = what.item
        if (item is AdaptivePatternItem) {
            return item.decode(what, level)
        }
        return null
    }
}
