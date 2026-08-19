package allyouneed.logic.crafting

import allyouneed.logic.crafting.PatternRecipe.WTF.SlowlyConsumed
import allyouneed.util.logger
import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import net.minecraft.world.level.Level
import java.math.BigInteger
import kotlin.math.roundToLong

/**
 * 用 ojalgo 求解合成计划的 MIP 建模，带大数折叠分解。
 *
 * 数值核心（MIP 建模、折叠分解、目标库存一次性记账）在 [CraftingSolverCore]，
 * 本类只负责把 [CraftingSolverCore.Solve] 包装成带 AE 类型的 [Result]：
 * patternTimes / usedItems / emittedItems / missingItems，以及工具损耗回填。
 *
 * ## 建模要点（详见 [CraftingSolverCore]）
 * - 只建模 sources（CompletelyConsumed + ByProduct 消耗）与 targets（产出），
 *   Constant 催化剂不消耗、不产出，直接忽略。
 * - SlowlyConsumed 工具（lossy catalyst）不做预估：主树定死后按执行次数
 *   循环调用 [IPatternDetails.IInput.getRemainingKey] 计算真实工具损耗。
 * - 两阶段：`minimise Σ x_r` 满足目标 → 最优计划；不可行时 `maximise 目标产出`
 *   → 最大可达产量 P，缺货 = Q − P，天然实现 CRAFT_LESS。
 * - 需求超过精确域时按 [CraftingSolverCore.FOLD_BASE] 折叠放大，[BigInteger]
 *   精确校验库存倍率；目标物品自身库存只一次性满足，不参与放大。
 */
class MipCraftingPlanner(
    private val snapshot: CraftingInventorySnapshot,
    private val level: Level?,
    private val output: AEKey,
    private val requestedAmount: Long,
) {
    private val nItems = snapshot.resources.size
    private val mRecipes = snapshot.recipes.size
    private val targetId: Int = snapshot.keyIndex.getOrDefault(output, -1)
    /** 初始库存，BigInteger 精确表示。 */
    private val baseStock = Array(nItems) { i -> snapshot.resources[i].stack.valBig }
    private val isEmitter = BooleanArray(mRecipes) { r -> snapshot.recipes[r].pattern.contains(-1) }
    /** deltaByItem[item] = map(recipe -> 净变化)，正 = 产出，负 = 消耗。 */
    private val deltaByItem = Array(nItems) { HashMap<Int, Long>() }

    private val core = CraftingSolverCore(
        nItems = nItems,
        mRecipes = mRecipes,
        deltaByItem = deltaByItem,
        targetId = targetId,
        isEmitter = isEmitter,
    )

    init {
        if (targetId < 0) {
            throw IllegalStateException("Target $output is not reachable in the crafting snapshot")
        }
        for ((r, recipe) in snapshot.recipes.withIndex()) {
            for (ref in recipe.sources) deltaByItem[ref.id].merge(r, -ref.amount, Long::plus)
            for (ref in recipe.targets) deltaByItem[ref.id].merge(r, ref.amount, Long::plus)
        }
    }

    data class Result(
        val finalAmount: Long,
        val patternTimes: Map<IPatternDetails, Long>,
        val usedItems: KeyCounter,
        val emittedItems: KeyCounter,
        val missingItems: KeyCounter,
        val bytes: Long,
        val simulation: Boolean,
        val multiplePaths: Boolean,
        /** 各物品净消耗（正 = 消耗），BigInteger 精确，供折叠放大校验。 */
        val net: Array<BigInteger>,
    )

    fun plan(): Result {
        try {
            return buildResult(core.plan(requestedAmount, baseStock.copyOf()))
        } catch (e: Throwable) {
            logger.error("MIP solver failed for ${requestedAmount}x$output", e)
            return missingResult(requestedAmount)
        }
    }

    /** 把纯数值解包装成带 AE 类型的 [Result]：工具损耗回填 + 缺货/使用量精确记账。 */
    private fun buildResult(solve: CraftingSolverCore.Solve): Result {
        val patternTimes = HashMap<IPatternDetails, Long>()
        for ((r, recipe) in snapshot.recipes.withIndex()) {
            val times = solve.xs[r]
            if (times <= 0) continue
            val pid = recipe.pattern.firstOrNull { it >= 0 } ?: continue
            patternTimes.merge(snapshot.patterns[pid], times) { a, b -> a + b }
        }
        val bytes = patternTimes.values.sum() * 8

        val usedItems = KeyCounter()
        for (i in 0 until nItems) {
            if (solve.net[i] <= BigInteger.ZERO) continue
            usedItems.add(
                snapshot.resources[i].stack.key,
                solve.net[i].min(Long.MAX_VALUE.toBigInteger()).toLong()
            )
        }
        val toolLoss = toolLossByKey(solve.xs)
        for ((tool, loss) in toolLoss) usedItems.add(tool, loss)

        val missing = KeyCounter()
        if (solve.missingOutput > 0) missing.add(output, solve.missingOutput)

        // 净需求超过库存（BigInteger 精确）→ 截断并记为缺货
        var inventoryShort = false
        for (i in 0 until nItems) {
            val key = snapshot.resources[i].stack.key
            val need = BigInteger.valueOf(usedItems.get(key))
            val have = baseStock[i]
            if (need > have) {
                missing.add(key, need.subtract(have).min(Long.MAX_VALUE.toBigInteger()).toLong())
                usedItems.set(key, have.min(Long.MAX_VALUE.toBigInteger()).toLong())
                inventoryShort = true
            }
        }

        val emittedItems = KeyCounter()
        for ((r, recipe) in snapshot.recipes.withIndex()) {
            if (solve.xs[r] <= 0 || !isEmitter[r]) continue
            for (ref in recipe.targets) {
                emittedItems.add(
                    snapshot.resources[ref.id].stack.key,
                    (ref.amount.toDouble() * solve.xs[r]).roundToLong()
                )
            }
        }

        return Result(
            finalAmount = solve.finalAmount,
            patternTimes = patternTimes,
            usedItems = usedItems,
            emittedItems = emittedItems,
            missingItems = missing,
            bytes = bytes,
            simulation = solve.simulation || inventoryShort,
            multiplePaths = snapshot.resources.any { it.recipes.size > 1 },
            net = solve.net,
        )
    }

    private fun missingResult(amount: Long): Result {
        val missing = KeyCounter()
        missing.add(output, amount)
        return Result(
            finalAmount = 0,
            patternTimes = emptyMap(),
            usedItems = KeyCounter(),
            emittedItems = KeyCounter(),
            missingItems = missing,
            bytes = 0,
            simulation = true,
            multiplePaths = snapshot.resources.any { it.recipes.size > 1 },
            net = Array(nItems) { BigInteger.ZERO },
        )
    }

    // ------------------------------------------------------------
    // 工具损耗：主树定死后循环 getRemainingKey
    // ------------------------------------------------------------

    private fun toolLossByKey(xs: LongArray): Map<AEKey, Long> {
        val result = HashMap<AEKey, Long>()
        for ((r, recipe) in snapshot.recipes.withIndex()) {
            val times = xs[r]
            if (times <= 0) continue
            val pid = recipe.pattern.firstOrNull { it >= 0 } ?: continue
            val pattern = snapshot.patterns[pid]
            for (input in pattern.inputs) {
                val possible = input.possibleInputs
                if (possible.isEmpty()) continue
                val tool = possible[0].what
                if (PatternRecipe.wtfIsThis(level, input, tool) != SlowlyConsumed) continue
                val amountPerRun = possible[0].amount
                val lostPerUnit = countToolLoss(input, tool, times)
                if (lostPerUnit > 0) {
                    result.merge(tool, (lostPerUnit.toDouble() * amountPerRun).roundToLong()) { a, b -> a + b }
                }
            }
        }
        return result
    }

    /**
     * 计算"一把工具执行 [times] 次"后被报废的数量（net 损耗）。
     *
     * 每执行一次调用 [IPatternDetails.IInput.getRemainingKey]：
     * - 返回 null 表示当前工具报废，损耗 +1，换一把满耐久工具继续
     * - 返回剩余 key 表示耐久下降，继续使用
     *
     * [times] 很大时用耐久探测：先跑一遍求"每把可执行次数"，再做除法。
     */
    private fun countToolLoss(input: IPatternDetails.IInput, full: AEKey, times: Long): Long {
        if (times <= 0) return 0
        if (times <= MAX_TOOL_LOOP) {
            var lost = 0L
            var current = full
            var remaining = times
            while (remaining > 0) {
                val next = input.getRemainingKey(current)
                if (next == null) {
                    lost++
                    current = full
                } else {
                    current = next
                }
                remaining--
            }
            return lost
        }
        var uses = 0L
        var cur = full
        while (true) {
            val next = input.getRemainingKey(cur)
            if (next == null) {
                uses++
                break
            }
            cur = next
            uses++
            if (uses > times) break
        }
        return times / uses
    }

    private companion object {
        /** 工具损耗精确循环的迭代上限，超过后改用耐久探测除法估算。 */
        const val MAX_TOOL_LOOP = 1_000_000L
    }
}
