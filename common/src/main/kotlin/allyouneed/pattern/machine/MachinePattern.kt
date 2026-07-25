package allyouneed.pattern.machine

import allyouneed.api.machine.MachineType
import allyouneed.api.machine.MachineTypeRegistry
import allyouneed.pattern.AEPatternUtil
import allyouneed.pattern.MachinePatternDecoder
import appeng.api.crafting.IPatternDetails
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.GenericStack
import appeng.crafting.pattern.EncodedPatternItem
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Machine-specific pattern item.
 */
class MachinePatternItem(props: Properties) : EncodedPatternItem(props) {
    init {
        // Register decoder at construction time (like ProbabilityPatternItem in references)
        PatternDetailsHelper.registerDecoder(MachinePatternDecoder)
    }

    fun encode(
        machineTypeId: ResourceLocation,
        inStacks: Array<GenericStack>,
        outStacks: Array<GenericStack>,
    ): ItemStack = ItemStack(this).apply {
        tag = CompoundTag().apply {
            putString("machineType", machineTypeId.toString())
            put("in", ListTag().apply {
                for (gs in inStacks) add(GenericStack.writeTag(gs))
            })
            put("out", ListTag().apply {
                for (gs in outStacks) add(GenericStack.writeTag(gs))
            })
        }
    }

    override fun decode(stack: ItemStack, level: Level, tryRecovery: Boolean): IPatternDetails? {
        val key = AEItemKey.of(stack) ?: return null
        return decode(key, level)
    }

    override fun decode(what: AEItemKey, level: Level): IPatternDetails? {
        if (!what.hasTag()) return null
        return runCatching { AEMachinePattern(what, level) }.getOrNull()
    }
}

/**
 * Decoded machine pattern details.
 * We expose `getMachineType()` for Java Mixin code.
 */
class AEMachinePattern(
    private val definition: AEItemKey, private val level: Level
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

    /**
     * Called from Java Mixin for CRAFTING_GRID machine patterns.
     * Return null for now (we drive manually or let assembler handle via other paths).
     */
    fun asAssemblerPatternOrNull(): appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern? = null

    override fun equals(other: Any?): Boolean =
        other is AEMachinePattern && other.definition == this.definition

    override fun hashCode(): Int = definition.hashCode()

    private class Input(private val stack: GenericStack) : IPatternDetails.IInput {
        override fun getMultiplier(): Long = stack.amount()
        override fun getPossibleInputs(): Array<GenericStack> = arrayOf(stack)
        override fun isValid(what: appeng.api.stacks.AEKey, level: Level): Boolean = what.matches(stack)
        override fun getRemainingKey(what: appeng.api.stacks.AEKey): appeng.api.stacks.AEKey? = null
    }
}
