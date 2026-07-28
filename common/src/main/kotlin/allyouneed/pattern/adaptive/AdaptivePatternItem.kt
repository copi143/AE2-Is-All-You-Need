package allyouneed.pattern.adaptive

import allyouneed.pattern.AEPatternUtil
import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.GenericStack
import appeng.crafting.pattern.EncodedPatternItem
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class AdaptivePatternItem(props: Properties) : EncodedPatternItem(props) {
    init {
        PatternDetailsHelper.registerDecoder(AdaptivePatternDecoder)
    }

    fun encode(
        inputsPerAttempt: List<GenericStack>,
        output: GenericStack,
        probability: Double,
        timeout: Int
    ): ItemStack {
        val tag = CompoundTag()

        val inList = ListTag()
        for (gs in inputsPerAttempt) {
            inList.add(GenericStack.writeTag(gs))
        }
        tag.put(AdaptiveStatisticalPattern.NBT_KEY_INPUTS, inList)
        tag.put(AdaptiveStatisticalPattern.NBT_KEY_OUTPUT, GenericStack.writeTag(output))
        tag.putDouble(AdaptiveStatisticalPattern.NBT_KEY_PROBABILITY, probability.coerceIn(0.01, 1.0))
        tag.putInt(AdaptiveStatisticalPattern.NBT_KEY_TIMEOUT, timeout.coerceAtLeast(1))

        val stack = ItemStack(this)
        stack.tag = tag
        return stack
    }

    override fun decode(stack: ItemStack, level: Level, tryRecovery: Boolean): IPatternDetails? {
        val key = AEItemKey.of(stack) ?: return null
        return decode(key, level)
    }

    override fun decode(what: AEItemKey, level: Level): IPatternDetails? {
        if (!what.hasTag()) return null
        return runCatching { AdaptiveStatisticalPattern.decode(what) }.getOrNull()
    }
}
