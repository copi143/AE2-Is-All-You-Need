package allyouneed.logic.crafting

import allyouneed.logic.crafting.PatternRecipe.WTF.*
import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEItemKey
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import net.minecraft.world.level.Level

data class PatternRecipe(
    val sources: ArrayList<GenericStack>,
    val targets: ArrayList<GenericStack>,
    val catalysts: ArrayList<Catalyst>,
) {
    private constructor() : this(ArrayList(), ArrayList(), ArrayList())

    data class Catalyst(val stack: GenericStack, val lossy: Boolean)

    /**
     * - [CompletelyConsumed] 会完全消耗的材料
     * - [SlowlyConsumed] 会消耗耐久的工具
     * - [Constant] 完全不会消耗的材料
     * - [ByProduct] 消耗后完全变成另一个东西
     */
    enum class WTF { CompletelyConsumed, SlowlyConsumed, Constant, ByProduct }

    fun wtfIsThis(level: Level? = null, input: IPatternDetails.IInput, key: AEKey): WTF {
        val withoutNBT = key.dropSecondary()
        val baseKey = if (level == null || input.isValid(withoutNBT, level)) {
            withoutNBT
        } else {
            key
        }

        val rem = input.getRemainingKey(baseKey) ?: return CompletelyConsumed
        // 这边再尝试合成一次因为有可能 rem 被合成步骤打上 nbt 导致与 baseKey 不同
        // 再尝试一次应该 nbt 不会变化，对于很奇怪的实现我们打算不兼容
        val remrem = input.getRemainingKey(rem)
        if (remrem == rem) return Constant
        if (rem is AEItemKey && rem.isDamaged) return SlowlyConsumed

        return ByProduct // 实在猜不出来
    }

    private fun processInput(level: Level? = null, input: IPatternDetails.IInput, index: Int) {
        val stack = input.possibleInputs[index]
        val wtf = wtfIsThis(level, input, stack.what)

        when (wtf) {
            CompletelyConsumed -> sources.add(stack)
            SlowlyConsumed -> catalysts.add(Catalyst(stack, true))
            Constant -> catalysts.add(Catalyst(stack, false))
            ByProduct -> {
                sources.add(stack)
                targets.add(GenericStack(input.getRemainingKey(stack.what), stack.amount))
            }
        }
    }

    private fun processOutput(output: GenericStack) {
        targets.add(output) // 这可太 TM 简单了
    }

    companion object {
        fun firstOf(level: Level? = null, pattern: IPatternDetails): PatternRecipe {
            val pr = PatternRecipe()
            pattern.outputs.forEach { output ->
                pr.processOutput(output)
            }
            pattern.inputs.forEach { input ->
                pr.processInput(level, input, 0)
            }
            return pr
        }

        fun fuzzy(level: Level? = null, snapshot: InventorySnapshot, pattern: IPatternDetails): List<PatternRecipe> {
//            snapshot.fuzzy()
//            pattern.inputs.forEach {
//
//            }

            TODO()
        }
    }
}
