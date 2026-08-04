package allyouneed.pattern.adaptive

import appeng.api.stacks.GenericStack
import appeng.helpers.IPatternTerminalLogicHost
import appeng.parts.encoding.PatternEncodingLogic
import appeng.api.inventories.InternalInventory
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.Mth

class AdaptivePatternEncodingLogic(host: IPatternTerminalLogicHost) : PatternEncodingLogic(host) {
    var probability: Double = 0.8
        private set
    var timeout: Int = 30
        private set

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

        if (inv == encodedPatternInv) {
            val pattern = encodedPatternInv.getStackInSlot(0)
            if (!pattern.isEmpty && pattern.item is AdaptivePatternItem) {
                loadAdaptivePattern(pattern)
            }
        }
    }

    private fun loadAdaptivePattern(pattern: net.minecraft.world.item.ItemStack) {
        val tag = pattern.tag ?: return
        if (!tag.contains(AdaptiveStatisticalPattern.NBT_KEY_INPUTS)) return

        mode = appeng.parts.encoding.EncodingMode.PROCESSING

        val inputList = tag.getList(AdaptiveStatisticalPattern.NBT_KEY_INPUTS, net.minecraft.nbt.Tag.TAG_COMPOUND.toInt())
        val inputs = inputList.indices.mapNotNull { GenericStack.readTag(inputList.getCompound(it)) }

        val outputTag = tag.getCompound(AdaptiveStatisticalPattern.NBT_KEY_OUTPUT)
        val output = GenericStack.readTag(outputTag)

        probability = tag.getDouble(AdaptiveStatisticalPattern.NBT_KEY_PROBABILITY).coerceIn(0.01, 1.0)
        timeout = tag.getInt(AdaptiveStatisticalPattern.NBT_KEY_TIMEOUT).coerceAtLeast(1)

        val inputInv = encodedInputInv
        val outputInv = encodedOutputInv

        inputInv.beginBatch()
        try {
            for (i in 0 until inputInv.size()) {
                inputInv.setStack(i, if (i < inputs.size) inputs[i] else null)
            }
        } finally {
            inputInv.endBatch()
        }

        outputInv.beginBatch()
        try {
            if (output != null) {
                outputInv.setStack(0, output)
            }
            for (i in 1 until outputInv.size()) {
                outputInv.setStack(i, null)
            }
        } finally {
            outputInv.endBatch()
        }

        saveChanges()
    }

    override fun readFromNBT(tag: CompoundTag) {
        super.readFromNBT(tag)
        if (tag.contains("adaptive_probability")) {
            probability = tag.getDouble("adaptive_probability").coerceIn(0.01, 1.0)
        }
        if (tag.contains("adaptive_timeout")) {
            timeout = tag.getInt("adaptive_timeout").coerceAtLeast(1)
        }
    }

    override fun writeToNBT(tag: CompoundTag) {
        super.writeToNBT(tag)
        tag.putDouble("adaptive_probability", probability)
        tag.putInt("adaptive_timeout", timeout)
    }
}
