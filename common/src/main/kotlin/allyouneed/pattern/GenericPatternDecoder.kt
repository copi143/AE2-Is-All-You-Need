package allyouneed.pattern

import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.IPatternDetailsDecoder
import appeng.api.stacks.AEItemKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Generic decoder that delegates to any [ModEncodedPatternItem]'s [decode][EncodedPatternItem.decode]
 * method. Replaces the three per-type decoder objects (AdaptivePatternDecoder,
 * MachinePatternDecoder, PseudoPatternDecoder). Only matches [ModEncodedPatternItem]
 * subclasses, so AE2's own patterns and other mods' patterns are unaffected.
 */
object GenericPatternDecoder : IPatternDetailsDecoder {
    override fun isEncodedPattern(stack: ItemStack): Boolean = stack.item is ModEncodedPatternItem

    override fun decodePattern(what: AEItemKey, level: Level): IPatternDetails? {
        val item = what.item
        return if (item is ModEncodedPatternItem) item.decode(what, level) else null
    }

    override fun decodePattern(stack: ItemStack, level: Level, tryRecovery: Boolean): IPatternDetails? {
        val item = stack.item
        return if (item is ModEncodedPatternItem) item.decode(stack, level, tryRecovery) else null
    }
}
