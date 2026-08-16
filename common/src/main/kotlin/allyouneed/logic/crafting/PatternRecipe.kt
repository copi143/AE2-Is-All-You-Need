package allyouneed.logic.crafting

import allyouneed.logic.crafting.PatternRecipe.WTF.*
import allyouneed.util.debugLogger
import allyouneed.util.logger
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

    private fun processInput(level: Level?, input: IPatternDetails.IInput, index: Int) {
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
        fun wtfIsThis(level: Level?, input: IPatternDetails.IInput, key: AEKey): WTF {
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

        fun <T> cartesianProduct(lists: List<List<T>>): List<List<T>> {
            return lists.fold(listOf(emptyList())) { acc, list ->
                acc.flatMap { prefix ->
                    list.map { element -> prefix + element }
                }
            }
        }

        fun firstOf(level: Level?, pattern: IPatternDetails): PatternRecipe {
            val pr = PatternRecipe()
            pattern.outputs.forEach { output ->
                pr.processOutput(output)
            }
            pattern.inputs.forEach { input ->
                pr.processInput(level, input, 0)
            }
            return pr
        }

        fun fuzzy(level: Level?, snapshot: InventorySnapshot, pattern: IPatternDetails): List<PatternRecipe> {
            fun makePatternRecipe() = PatternRecipe().apply {
                pattern.outputs.forEach { output ->
                    this.processOutput(output)
                }
            }

            data class Field(val input: IPatternDetails.IInput, val stack: GenericStack, val wtf: WTF)
            data class FieldKey(val key: AEKey, val amount: Long, val wtf: WTF)

            val inputs = pattern.inputs.map { input ->
                if (input.possibleInputs.size > 1) {
                    debugLogger.info("possibleInputs size: {}", input.possibleInputs.size)
                }

                // Multiple possibleInputs often resolve to the same fuzzy key. Deduplicate before
                // taking the product, otherwise identical recipes multiply every later dimension.
                val fields = ArrayList<Field>()
                val seen = HashSet<FieldKey>()
                for (stack in input.possibleInputs) {
                    val amount = stack.amount * input.multiplier
                    for (key in snapshot.fuzzy(stack.what)) {
                        if (level != null && !input.isValid(key, level)) continue
                        val wtf = wtfIsThis(level, input, key)
                        if (seen.add(FieldKey(key, amount, wtf))) {
                            fields.add(Field(input, GenericStack(key, amount), wtf))
                        }
                    }
                }
                fields
            }

            var size = 1L
            for (input in inputs) {
                if (input.isEmpty()) return emptyList()
                if (size > MAX_FUZZY_RECIPES / input.size) {
                    throw LooksLikeDosAttack("cartesianProduct size: >$MAX_FUZZY_RECIPES")
                }
                size *= input.size
            }
            if (size > 128) logger.info("Too large: {}", size)
            if (size > MAX_FUZZY_RECIPES) throw LooksLikeDosAttack("cartesianProduct size: $size")

            // Generate leaves directly. The old fold/flatMap implementation allocated every
            // intermediate prefix list, which becomes dominant for wide fuzzy patterns.
            val result = ArrayList<PatternRecipe>(size.toInt())
            val selected = arrayOfNulls<Field>(inputs.size)
            fun generate(depth: Int) {
                if (depth < inputs.size) {
                    for (field in inputs[depth]) {
                        selected[depth] = field
                        generate(depth + 1)
                    }
                    return
                }

                val pr = makePatternRecipe()
                for (field in selected) {
                    val (input, stack, wtf) = field!!
                    when (wtf) {
                        CompletelyConsumed -> pr.sources.add(stack)
                        SlowlyConsumed -> pr.catalysts.add(Catalyst(stack, true))
                        Constant -> pr.catalysts.add(Catalyst(stack, false))
                        ByProduct -> {
                            pr.sources.add(stack)
                            pr.targets.add(GenericStack(input.getRemainingKey(stack.what), stack.amount))
                        }
                    }
                }
                result.add(pr)
            }
            generate(0)
            return result
        }

        private const val MAX_FUZZY_RECIPES = 4096L
    }
}
