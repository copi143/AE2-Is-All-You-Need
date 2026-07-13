package allyouneed.pattern.machine

import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.GenericStack
import appeng.crafting.pattern.EncodedPatternItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import allyouneed.api.machine.MachineType
import allyouneed.api.machine.MachineTypeRegistry
import allyouneed.pattern.AEPatternUtil

/**
 * Machine-specific pattern item.
 */
class MachinePatternItem(props: Item.Properties) : EncodedPatternItem(props) {

    fun encode(machineTypeId: ResourceLocation, inStacks: Array<GenericStack?>, outStacks: Array<GenericStack?>): ItemStack {
        val tag = CompoundTag()
        tag.putString("machineType", machineTypeId.toString())

        val inList = ListTag()
        for (gs in inStacks) if (gs != null) inList.add(GenericStack.writeTag(gs))
        tag.put("in", inList)

        val outList = ListTag()
        for (gs in outStacks) if (gs != null) outList.add(GenericStack.writeTag(gs))
        tag.put("out", outList)

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
        return try {
            AEMachinePattern(what, level)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Decoded machine pattern details.
 * We expose `getMachineType()` for Java Mixin code.
 */
class AEMachinePattern(
    private val definition: AEItemKey,
    private val level: Level
) : IPatternDetails {

    val machineTypeId: ResourceLocation
    private val _machineType: MachineType
    val sparseInputs: Array<GenericStack?>
    val sparseOutputs: Array<GenericStack?>
    private val inputs: Array<IPatternDetails.IInput>
    private val condensedOutputs: Array<GenericStack>

    init {
        val tag = definition.tag ?: throw IllegalStateException("Machine pattern without tag")
        machineTypeId = ResourceLocation(tag.getString("machineType"))
        _machineType = MachineTypeRegistry.get(machineTypeId)
            ?: throw IllegalArgumentException("Unknown machine type: $machineTypeId")

        sparseInputs = readArray(tag, "in")
        sparseOutputs = readArray(tag, "out")

        val condensed = AEPatternUtil.condenseStacks(sparseInputs)
        inputs = Array(condensed.size) { i -> Input(condensed[i]) }
        condensedOutputs = AEPatternUtil.condenseStacks(sparseOutputs)
    }

    /**
     * Java-friendly getter.
     */
    fun getMachineType(): MachineType = _machineType

    private fun readArray(tag: CompoundTag, key: String): Array<GenericStack?> {
        if (!tag.contains(key)) return emptyArray()
        val list = tag.getList(key, Tag.TAG_COMPOUND.toInt())
        val arr = arrayOfNulls<GenericStack>(list.size)
        for (i in 0 until list.size) {
            arr[i] = GenericStack.readTag(list.getCompound(i))
        }
        return arr
    }

    override fun getDefinition(): AEItemKey = definition
    override fun getInputs(): Array<IPatternDetails.IInput> = inputs
    override fun getOutputs(): Array<GenericStack> = condensedOutputs

    fun asAssemblerPatternOrNull(): appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern? = null

    private class Input(private val stack: GenericStack) : IPatternDetails.IInput {
        override fun getMultiplier(): Long = stack.amount()
        override fun getPossibleInputs(): Array<GenericStack> = arrayOf(stack)
        override fun isValid(what: appeng.api.stacks.AEKey, level: Level): Boolean = what.matches(stack)
        override fun getRemainingKey(what: appeng.api.stacks.AEKey): appeng.api.stacks.AEKey? = null
    }
}
