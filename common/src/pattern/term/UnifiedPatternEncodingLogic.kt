package allyouneed.pattern.term

import allyouneed.logic.machine.MachineType
import allyouneed.pattern.adaptive.AdaptivePatternItem
import allyouneed.pattern.adaptive.AdaptiveStatisticalPattern
import allyouneed.pattern.machine.MachinePatternItem
import allyouneed.pattern.machine.MachinePatternTags
import allyouneed.pattern.pseudo.PseudoPatternItem
import appeng.api.crafting.PatternDetailsHelper
import appeng.api.inventories.InternalInventory
import appeng.api.stacks.GenericStack
import appeng.crafting.pattern.AEProcessingPattern
import appeng.helpers.IPatternTerminalLogicHost
import appeng.parts.encoding.EncodingMode
import appeng.parts.encoding.PatternEncodingLogic
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack

class UnifiedPatternEncodingLogic(
    private val logicHost: IPatternTerminalLogicHost,
) : PatternEncodingLogic(logicHost) {

    var kind: EncodingKind = EncodingKind.MACHINE
        private set

    var virtualMachineTypeId: String = firstMachineId()
        private set

    val virtualMachineType: MachineType?
        get() = MachineType.byId(virtualMachineTypeId)

    var probability: Double = 0.8
        private set

    var timeout: Int = 30
        private set

    fun setKind(kind: EncodingKind) {
        if (this.kind != kind) {
            this.kind = kind
        }
        mode = EncodingMode.PROCESSING
        saveChanges()
    }

    override fun getMode(): EncodingMode = EncodingMode.PROCESSING

    override fun setMode(mode: EncodingMode) {
        super.setMode(EncodingMode.PROCESSING)
    }

    fun setVirtualMachineType(id: String) {
        if (MachineType.byId(id) != null && id != virtualMachineTypeId) {
            virtualMachineTypeId = id
            saveChanges()
        }
    }

    fun setProbability(probability: Double) {
        this.probability = Mth.clamp(probability, 0.01, 1.0)
        saveChanges()
    }

    fun setTimeout(timeout: Int) {
        this.timeout = Mth.clamp(timeout, 1, 3600)
        saveChanges()
    }

    override fun onChangeInventory(inv: InternalInventory, slot: Int) {
        super.onChangeInventory(inv, slot)
        mode = EncodingMode.PROCESSING
        if (inv === encodedPatternInv) {
            loadUnifiedPattern(encodedPatternInv.getStackInSlot(0))
        }
    }

    private fun loadUnifiedPattern(pattern: ItemStack) {
        if (pattern.isEmpty) return
        when (pattern.item) {
            is MachinePatternItem -> loadMachinePattern(pattern)
            is AdaptivePatternItem -> loadAdaptivePattern(pattern)
            is PseudoPatternItem -> loadPseudoPattern(pattern)
            else -> {
                val details = PatternDetailsHelper.decodePattern(pattern, logicHost.level)
                if (details is AEProcessingPattern) {
                    kind = EncodingKind.PROCESSING
                }
            }
        }
        mode = EncodingMode.PROCESSING
        saveChanges()
    }

    private fun loadMachinePattern(pattern: ItemStack) {
        val tag = pattern.tag ?: return
        if (!tag.contains(MachinePatternTags.MACHINE_TYPE, Tag.TAG_STRING.toInt())) return
        val typeId = tag.getString(MachinePatternTags.MACHINE_TYPE)
        if (MachineType.byId(typeId) == null) return

        kind = EncodingKind.MACHINE
        virtualMachineTypeId = typeId

        val inputList = tag.getList(MachinePatternTags.INPUTS, Tag.TAG_COMPOUND.toInt())
        val inputs = inputList.indices.map { GenericStack.readTag(inputList.getCompound(it)) }
        val output = GenericStack.readTag(tag.getCompound(MachinePatternTags.OUTPUT))
        fillInputs(inputs)
        fillOutputs(listOf(output))
    }

    private fun loadAdaptivePattern(pattern: ItemStack) {
        val tag = pattern.tag ?: return
        if (!tag.contains(AdaptiveStatisticalPattern.NBT_KEY_INPUTS)) return

        kind = EncodingKind.PROBABILITY
        val inputList = tag.getList(AdaptiveStatisticalPattern.NBT_KEY_INPUTS, Tag.TAG_COMPOUND.toInt())
        val inputs = inputList.indices.mapNotNull { GenericStack.readTag(inputList.getCompound(it)) }
        val output = GenericStack.readTag(tag.getCompound(AdaptiveStatisticalPattern.NBT_KEY_OUTPUT))
        probability = tag.getDouble(AdaptiveStatisticalPattern.NBT_KEY_PROBABILITY).coerceIn(0.01, 1.0)
        timeout = tag.getInt(AdaptiveStatisticalPattern.NBT_KEY_TIMEOUT).coerceAtLeast(1)
        fillInputs(inputs)
        fillOutputs(listOf(output))
    }

    private fun loadPseudoPattern(pattern: ItemStack) {
        val tag = pattern.tag ?: return
        if (!tag.contains("in")) return

        kind = EncodingKind.PSEUDO
        val inputList = tag.getList("in", Tag.TAG_COMPOUND.toInt())
        val inputs = inputList.indices.mapNotNull { GenericStack.readTag(inputList.getCompound(it)) }
        fillInputs(inputs)
        fillOutputs(emptyList())
    }

    private fun fillInputs(inputs: List<GenericStack?>) {
        val inputInv = encodedInputInv
        inputInv.beginBatch()
        try {
            for (i in 0 until inputInv.size()) {
                inputInv.setStack(i, if (i < inputs.size) inputs[i] else null)
            }
        } finally {
            inputInv.endBatch()
        }
    }

    private fun fillOutputs(outputs: List<GenericStack?>) {
        val outputInv = encodedOutputInv
        outputInv.beginBatch()
        try {
            for (i in 0 until outputInv.size()) {
                outputInv.setStack(i, if (i < outputs.size) outputs[i] else null)
            }
        } finally {
            outputInv.endBatch()
        }
    }

    override fun readFromNBT(tag: CompoundTag) {
        super.readFromNBT(tag)
        if (tag.contains(NBT_KIND, Tag.TAG_STRING.toInt())) {
            kind = EncodingKind.byName(tag.getString(NBT_KIND))
        }
        if (tag.contains(MachinePatternTags.MACHINE_TYPE, Tag.TAG_STRING.toInt())) {
            val id = tag.getString(MachinePatternTags.MACHINE_TYPE)
            if (MachineType.byId(id) != null) {
                virtualMachineTypeId = id
            }
        }
        if (tag.contains(NBT_PROBABILITY)) {
            probability = tag.getDouble(NBT_PROBABILITY).coerceIn(0.01, 1.0)
        }
        if (tag.contains(NBT_TIMEOUT)) {
            timeout = tag.getInt(NBT_TIMEOUT).coerceAtLeast(1)
        }
        mode = EncodingMode.PROCESSING
    }

    override fun writeToNBT(tag: CompoundTag) {
        mode = EncodingMode.PROCESSING
        super.writeToNBT(tag)
        tag.putString(NBT_KIND, kind.name)
        tag.putString(MachinePatternTags.MACHINE_TYPE, virtualMachineTypeId)
        tag.putDouble(NBT_PROBABILITY, probability)
        tag.putInt(NBT_TIMEOUT, timeout)
    }

    private fun firstMachineId(): String = MachineType.getAll().firstOrNull()?.id ?: ""

    companion object {
        const val NBT_KIND = "encodingKind"
        const val NBT_PROBABILITY = "adaptive_probability"
        const val NBT_TIMEOUT = "adaptive_timeout"
    }
}
