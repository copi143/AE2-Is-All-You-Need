package allyouneed.pattern.machine

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

class MachinePatternItem(props: Properties) : EncodedPatternItem(props) {
    init {
        PatternDetailsHelper.registerDecoder(MachinePatternDecoder)
    }

    fun encode(machineTypeId: String, inputs: Array<GenericStack?>, output: GenericStack): ItemStack {
        val tag = CompoundTag()
        tag.putString(MachinePatternTags.MACHINE_TYPE, machineTypeId)

        val inList = ListTag()
        for (gs in inputs) {
            if (gs != null) {
                inList.add(GenericStack.writeTag(gs))
            } else {
                inList.add(CompoundTag())
            }
        }
        tag.put(MachinePatternTags.INPUTS, inList)
        tag.put(MachinePatternTags.OUTPUT, GenericStack.writeTag(output))

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
        return runCatching { MachinePatternDetails(what) }.getOrNull()
    }
}
