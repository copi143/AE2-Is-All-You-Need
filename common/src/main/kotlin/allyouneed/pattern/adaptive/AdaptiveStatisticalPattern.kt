package allyouneed.pattern.adaptive

import allyouneed.pattern.AEPatternUtil
import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import net.minecraft.nbt.Tag
import net.minecraft.world.level.Level

/**
 * Adaptive probability pattern: instead of pre-computing a fixed number of attempts
 * via binomial distribution, this pattern uses a simpler ceil(N/p) scaling.
 *
 * The pattern encodes: per-attempt inputs, per-attempt output, probability p, and timeout T (seconds).
 * When the crafting tree requests N outputs, getInputs() returns inputs scaled by ceil(N/p).
 * The timeout T is stored for future adaptive retry logic.
 */
class AdaptiveStatisticalPattern(
    private val definition: AEItemKey,
    private val inputsPerAttempt: List<GenericStack>,
    private val output: GenericStack,
    val probability: Double,
    val timeout: Int,
    private val requestedOutputAmount: Long? = null
) : IPatternDetails {

    companion object {
        const val NBT_KEY_INPUTS = "adaptive_inputs"
        const val NBT_KEY_OUTPUT = "adaptive_output"
        const val NBT_KEY_PROBABILITY = "adaptive_probability"
        const val NBT_KEY_TIMEOUT = "adaptive_timeout"

        fun decode(what: AEItemKey): AdaptiveStatisticalPattern? {
            val tag = what.tag ?: return null
            if (!tag.contains(NBT_KEY_INPUTS) || !tag.contains(NBT_KEY_OUTPUT)) return null

            val inputList = tag.getList(NBT_KEY_INPUTS, Tag.TAG_COMPOUND.toInt())
            val inputs = inputList.indices.mapNotNull { GenericStack.readTag(inputList.getCompound(it)) }
            val output = GenericStack.readTag(tag.getCompound(NBT_KEY_OUTPUT)) ?: return null
            val probability = tag.getDouble(NBT_KEY_PROBABILITY).coerceIn(0.01, 1.0)
            val timeout = tag.getInt(NBT_KEY_TIMEOUT).coerceAtLeast(1)

            return AdaptiveStatisticalPattern(what, inputs, output, probability, timeout)
        }
    }

    private val condensedInputs: Array<GenericStack> by lazy {
        AEPatternUtil.condenseStacks(inputsPerAttempt.toTypedArray())
    }

    private val _inputs: Array<IPatternDetails.IInput> by lazy {
        val scale = getAttemptCount()
        condensedInputs.map { gs ->
            AdaptiveInput(gs.what(), gs.amount() * scale)
        }.toTypedArray()
    }

    private val _outputs: Array<GenericStack> by lazy {
        val amount = requestedOutputAmount ?: output.amount()
        arrayOf(GenericStack(output.what(), amount))
    }

    /**
     * Number of attempts to schedule: ceil(requestedOutput / outputPerAttempt / probability)
     * Simplified: ceil(N / p) where N = requested outputs
     */
    private fun getAttemptCount(): Long {
        val n = requestedOutputAmount ?: output.amount()
        if (probability >= 1.0) return n
        return Math.max(1, Math.ceil(n.toDouble() / probability).toLong())
    }

    fun forRequest(requestedOutputAmount: Long): AdaptiveStatisticalPattern {
        return AdaptiveStatisticalPattern(
            definition, inputsPerAttempt, output, probability, timeout,
            Math.max(1, requestedOutputAmount)
        )
    }

    override fun getDefinition(): AEItemKey = definition
    override fun getInputs(): Array<IPatternDetails.IInput> = _inputs
    override fun getOutputs(): Array<GenericStack> = _outputs

    override fun equals(other: Any?): Boolean =
        other is AdaptiveStatisticalPattern && other.definition == definition

    override fun hashCode(): Int = definition.hashCode()

    private class AdaptiveInput(
        private val key: AEKey,
        private val multiplier: Long
    ) : IPatternDetails.IInput {
        private val template = arrayOf(GenericStack(key, 1))
        override fun getMultiplier(): Long = multiplier
        override fun getPossibleInputs(): Array<GenericStack> = template
        override fun isValid(input: AEKey, level: Level): Boolean = input.matches(template[0])
        override fun getRemainingKey(template: AEKey): AEKey? = null
    }
}
