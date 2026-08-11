package allyouneed.logic.crafting

import allyouneed.logic.crafting.PatternRecipe.WTF.SlowlyConsumed
import allyouneed.util.logger
import appeng.api.crafting.IPatternDetails
import appeng.api.stacks.AEKey
import appeng.api.stacks.KeyCounter
import net.minecraft.world.level.Level
import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Variable
import kotlin.math.roundToLong

/**
 * 用 ojalgo 求解合成计划的 MIP 建模。
 *
 * 主树只建模"确定的部分"：
 * - [CraftingInventorySnapshot.ItemRef] 中的 sources（CompletelyConsumed + ByProduct 消耗）
 * - targets（产出 + ByProduct 产出）
 * - Constant 催化剂不消耗、不产出，直接忽略
 *
 * SlowlyConsumed 工具（lossy catalyst）**不做预估**：主树定死后再按执行次数
 * 循环调用 [IPatternDetails.IInput.getRemainingKey] 计算真实工具损耗。
 *
 * 两阶段：
 * 1. `minimise Σ x_r`，满足目标约束 → 最优计划（simulation=false）
 * 2. 阶段 1 不可行时 `maximise 目标产出`（发射配方固定为 0）→ 最大可达产量 P，
 *    缺货 = Q − P（simulation=true），天然实现 CRAFT_LESS
 */
class MipCraftingPlanner(
    private val snapshot: CraftingInventorySnapshot,
    private val level: Level?,
    private val output: AEKey,
    private val requestedAmount: Long,
) {
    companion object {
        /** double 可精确表示的整数上限（2^53）。超出时无法安全建模，直接报缺货。 */
        private const val MAX_EXACT = 1L shl 53
        /** 工具损耗精确循环的迭代上限，超过后改用耐久探测除法估算。 */
        private const val MAX_TOOL_LOOP = 1_000_000L
        /** 求解器软预算（ms）：达到即返回足够好的 DISTINCT 解。 */
        private const val TIME_SUFFICE_MS = 100L
        /** 求解器硬截止（ms）：用户要求的后台协程一分钟超时。 */
        private const val TIME_ABORT_MS = 60_000L
    }

    private val nItems = snapshot.resources.size
    private val mRecipes = snapshot.recipes.size
    private val targetId: Int = snapshot.keyIndex.getOrDefault(output, -1)
    /** 库存，封顶 2^53 保证 double 精确。 */
    private val stock = DoubleArray(nItems) { i ->
        minOf(snapshot.resources[i].stack.valLongSaturate.toDouble(), MAX_EXACT.toDouble())
    }
    private val isEmitter = BooleanArray(mRecipes) { r -> snapshot.recipes[r].pattern.contains(-1) }
    /** deltaByItem[item] = map(recipe -> 净变化)，正 = 产出，负 = 消耗。 */
    private val deltaByItem = Array(nItems) { HashMap<Int, Long>() }

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
    )

    fun plan(): Result {
        if (requestedAmount > MAX_EXACT) {
            logger.warn("MipCraftingPlanner: request ${requestedAmount}x$output exceeds 2^53, reporting missing")
            return missingOnly()
        }
        try {
            solveStage1()?.let { xs ->
                if (validate(xs)) return build(xs, simulation = false, stage2 = false)
            }
            solveStage2()?.let { xs ->
                if (validate(xs)) return build(xs, simulation = true, stage2 = true)
            }
        } catch (e: Throwable) {
            logger.error("MIP solver failed for ${requestedAmount}x$output", e)
        }
        return missingOnly()
    }

    // ------------------------------------------------------------
    // 求解
    // ------------------------------------------------------------

    private fun solveStage1(): LongArray? {
        val model = ExpressionsBasedModel()
        model.options.time_suffice = TIME_SUFFICE_MS
        model.options.time_abort = TIME_ABORT_MS
        val vars = ArrayList<Variable>(mRecipes)
        for (r in 0 until mRecipes) {
            vars.add(
                model.addVariable("x$r")
                    .integer(true)
                    .lower(0L)
                    .weight(if (isEmitter[r]) 0.0 else 1.0)
            )
        }
        addStockConstraints(model, vars)
        val target = model.addExpression("target")
        for ((r, delta) in deltaByItem[targetId]) target.set(vars[r], delta)
        target.lower((requestedAmount - stock[targetId].roundToLong()).coerceAtLeast(0L).toDouble())

        val result = model.minimise()
        if (!result.state.isFeasible) return null
        return extractSolution(vars)
    }

    private fun solveStage2(): LongArray? {
        val model = ExpressionsBasedModel()
        model.options.time_suffice = TIME_SUFFICE_MS
        model.options.time_abort = TIME_ABORT_MS
        val vars = ArrayList<Variable>(mRecipes)
        for (r in 0 until mRecipes) {
            val v = model.addVariable("x$r")
                .integer(true)
                .lower(0L)
            if (isEmitter[r]) v.upper(0L)
            vars.add(v)
        }
        addStockConstraints(model, vars)
        val objective = model.addExpression("objective")
        for ((r, delta) in deltaByItem[targetId]) if (delta > 0) objective.set(vars[r], delta)
        objective.weight(1.0)

        val result = model.maximise()
        if (!result.state.isFeasible) return null
        return extractSolution(vars)
    }

    private fun addStockConstraints(model: ExpressionsBasedModel, vars: List<Variable>) {
        for (i in 0 until nItems) {
            val deltas = deltaByItem[i]
            if (deltas.isEmpty()) continue
            val expr = model.addExpression("stock$i")
            for ((r, delta) in deltas) expr.set(vars[r], delta)
            // 库存守恒：库存 + SUM(delta*x) >= 0
            expr.lower(-stock[i])
        }
    }

    private fun extractSolution(vars: List<Variable>): LongArray {
        val xs = LongArray(mRecipes)
        for ((r, v) in vars.withIndex()) {
            val value = v.value ?: continue
            val count = Math.round(value.toDouble())
            xs[r] = if (count > 0) count else 0
        }
        return xs
    }

    /** 四舍五入后的整数解必须仍满足库存约束，容差 1。 */
    private fun validate(xs: LongArray): Boolean {
        for (i in 0 until nItems) {
            val deltas = deltaByItem[i]
            if (deltas.isEmpty()) continue
            var sum = 0.0
            for ((r, delta) in deltas) sum += delta * xs[r]
            if (sum < -stock[i] - 1.0) return false
        }
        return true
    }

    // ------------------------------------------------------------
    // 回填
    // ------------------------------------------------------------

    private fun build(xs: LongArray, simulation: Boolean, stage2: Boolean): Result {
        val patternTimes = HashMap<IPatternDetails, Long>()
        for ((r, recipe) in snapshot.recipes.withIndex()) {
            val times = xs[r]
            if (times <= 0) continue
            val pid = recipe.pattern.firstOrNull { it >= 0 } ?: continue
            patternTimes.merge(snapshot.patterns[pid], times) { a, b -> a + b }
        }
        val bytes = patternTimes.values.sum() * 8

        val usedItems = KeyCounter()
        for (i in 0 until nItems) {
            var delta = 0.0
            for ((r, d) in deltaByItem[i]) delta += d * xs[r]
            if (delta < 0) usedItems.add(snapshot.resources[i].stack.key, (-delta).roundToLong())
        }
        val toolLoss = toolLossByKey(xs)
        for ((tool, loss) in toolLoss) usedItems.add(tool, loss)

        val emittedItems = KeyCounter()
        for ((r, recipe) in snapshot.recipes.withIndex()) {
            if (xs[r] <= 0 || !isEmitter[r]) continue
            for (ref in recipe.targets) {
                emittedItems.add(snapshot.resources[ref.id].stack.key, (ref.amount.toDouble() * xs[r]).roundToLong())
            }
        }

        val missing = KeyCounter()
        var finalAmount = requestedAmount
        if (stage2) {
            var produced = 0.0
            for ((r, delta) in deltaByItem[targetId]) if (delta > 0) produced += delta * xs[r]
            val reachable = stock[targetId].roundToLong() + produced.roundToLong()
            finalAmount = minOf(requestedAmount, reachable)
            val shortfall = requestedAmount - finalAmount
            if (shortfall > 0) missing.add(output, shortfall)
        }

        // 物资/工具净需求超过库存 → 视为缺货（截断到库存，标记模拟态）
        var inventoryShort = false
        for (i in 0 until nItems) {
            val key = snapshot.resources[i].stack.key
            val need = usedItems.get(key)
            val have = stock[i].roundToLong()
            if (need > have) {
                missing.add(key, need - have)
                usedItems.set(key, have)
                inventoryShort = true
            }
        }

        return Result(
            finalAmount = finalAmount,
            patternTimes = patternTimes,
            usedItems = usedItems,
            emittedItems = emittedItems,
            missingItems = missing,
            bytes = bytes,
            simulation = simulation || inventoryShort,
            multiplePaths = snapshot.resources.any { it.recipes.size > 1 },
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

    private fun missingOnly(): Result {
        val missing = KeyCounter()
        missing.add(output, requestedAmount)
        return Result(
            finalAmount = 0,
            patternTimes = emptyMap(),
            usedItems = KeyCounter(),
            emittedItems = KeyCounter(),
            missingItems = missing,
            bytes = 0,
            simulation = true,
            multiplePaths = snapshot.resources.any { it.recipes.size > 1 },
        )
    }
}
