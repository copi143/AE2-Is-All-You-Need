package allyouneed.pattern

import allyouneed.pattern.machine.MachinePatternItem
import allyouneed.pattern.pseudo.PseudoPatternItem
import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.IPatternDetailsDecoder
import appeng.api.stacks.AEItemKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object MachinePatternDecoder : IPatternDetailsDecoder {
    override fun isEncodedPattern(stack: ItemStack): Boolean {
        return stack.item is MachinePatternItem
    }

    override fun decodePattern(stack: ItemStack, level: Level, tryRecovery: Boolean): IPatternDetails? {
        val item = stack.item
        if (item is MachinePatternItem) {
            return item.decode(stack, level, tryRecovery)
        }
        return null
    }

    override fun decodePattern(what: AEItemKey, level: Level): IPatternDetails? {
        val item = what.item
        if (item is MachinePatternItem) {
            return item.decode(what, level)
        }
        return null
    }
}

object PseudoPatternDecoder : IPatternDetailsDecoder {
    override fun isEncodedPattern(stack: ItemStack): Boolean {
        return stack.item is PseudoPatternItem
    }

    override fun decodePattern(stack: ItemStack, level: Level, tryRecovery: Boolean): IPatternDetails? {
        val item = stack.item
        if (item is PseudoPatternItem) {
            return item.decode(stack, level, tryRecovery)
        }
        return null
    }

    override fun decodePattern(what: AEItemKey, level: Level): IPatternDetails? {
        val item = what.item
        if (item is PseudoPatternItem) {
            return item.decode(what, level)
        }
        return null
    }
}
