package allyouneed.logic.crafting

import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Variable
import java.math.BigInteger
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 纯数值合成规划核心：不依赖任何 AE/MC 类型，可被单元测试直接构造。
 *
 * 承担 ojalgo MIP 建模、大数折叠分解、目标物品自身库存的一次性记账。
 * 输入输出全是普通 Kotlin 类型（Long / BigInteger / DoubleArray），
 * AE 类型（AEKey/IPatternDetails）的包装由 [MipCraftingPlanner] 完成。
 *
 * ## 两阶段
 * 1. `minimise Σ x_r`，满足目标约束 → 最优计划（simulation=false）
 * 2. 阶段 1 不可行时 `maximise 目标产出`（发射配方固定为 0）→ 最大可达产量 P，
 *    缺货 = Q − P（simulation=true），天然实现 CRAFT_LESS
 *
 * ## 大数折叠（需求 > [EXACT_LIMIT]）
 * 把配方图整体当作"基础模式"重复：
 * - 目标物品自身库存先一次性满足一部分需求（[fillOwnStock]），**不参与放大**，
 *   否则缩放会把"库存满足"误当成可无限放大的生产，产生幽灵产出
 *   （例如请求 10×FOLD_BASE、目标库存 3×FOLD_BASE 时会声称零合成就全额交付）；
 * - 对纯生产需求（目标库存清零后的 [foldPlan]）求一次精确 MIP 基解，
 *   用 [BigInteger] 对每个物品精确校验"库存能支撑几倍"，得到最大可放大倍率 k；
 * - 模式 ×k 合并，余数（rem < FOLD_BASE）再走精确域；
 * - 库存不足放不满时，剩余部分用"最大化产出"一次收尾，报告精确可达量。
 *
 * ## ojalgo 数值防护（[MAX_VAR]）
 * ojalgo 的整数 MIP 在约束常数（库存 RHS）很大时求解不稳定：库存 ≥ 2^32 量级
 * 会出现假 INFEASIBLE、甚至返回荒谬解（如 5×10^14 的合成次数）。给所有整数变量
 * 加显式上界 [MAX_VAR] 修复：精确域需求 ≤ 2^20，合法合成次数不会超过 2^20，
 * 2^24 有 16 倍余量且远低于崩溃量级。
 */
class CraftingSolverCore(
    private val nItems: Int,
    private val mRecipes: Int,
    private val deltaByItem: Array<out Map<Int, Long>>,
    private val targetId: Int,
    private val isEmitter: BooleanArray,
) {
    companion object {
        /** double 可精确表示的整数上限（2^53）。超出后 MIP 数值不可信，必须折叠。 */
        private const val MAX_EXACT = 1L shl 53
        /** 精确域上限：需求在此以下直接精确 MIP（合成次数 ≤ 2^20，double 完全精确）。 */
        const val EXACT_LIMIT = 1L shl 20
        /** 折叠模式规模（= [EXACT_LIMIT]，保证余数 rem < FOLD_BASE 落在精确域）。 */
        const val FOLD_BASE = 1L shl 20
        /** ojalgo 整数变量上界：超过后 ojalgo 整数 MIP 数值不稳定（见类注释）。 */
        const val MAX_VAR = 1L shl 24
        /** 求解器软预算（ms）：达到即返回足够好的 DISTINCT 解。 */
        private const val TIME_SUFFICE_MS = 100L
        /** 求解器硬截止（ms）。 */
        private const val TIME_ABORT_MS = 60_000L
    }

    /** 纯数值解：不含任何 AE 类型，由 [MipCraftingPlanner] 包装成对外 Result。 */
    data class Solve(
        /** 各配方执行次数。 */
        val xs: LongArray,
        /** 最终交付量（含目标物品自身库存回填）。 */
        val finalAmount: Long,
        /** 目标物品缺货量（>= 0）。 */
        val missingOutput: Long,
        /** 各物品净消耗（正 = 消耗），BigInteger 精确，供折叠放大校验。 */
        val net: Array<BigInteger>,
        /** 仿真/降级：stage2 最大化、子域库存不足、或 MIP 失败。 */
        val simulation: Boolean,
    )

    /**
     * 主入口：为 [amount] 的需求、基于 [stockBig] 库存求计划。
     *
     * 目标物品自身库存先一次性满足（[fillOwnStock]），其余需求折叠分解。
     */
    fun plan(amount: Long, stockBig: Array<BigInteger>): Solve {
        if (amount <= 0) return emptySolve()
        if (amount <= EXACT_LIMIT) return exactPlan(amount, stockBig)

        // 目标自身库存一次性满足，不参与放大（否则缩放会把"库存满足"误当成可放大的生产）
        val ownStock = minOf(stockBig[targetId], amount.toBigInteger()).toLong()
        if (ownStock >= amount) return exactPlan(amount, stockBig)

        // 纯生产库存：目标库存清零，保证 base 是"可安全 ×k 的纯生产基数"
        val prodStock = stockBig.copyOf()
        prodStock[targetId] = BigInteger.ZERO

        val produced = foldPlan(amount - ownStock, prodStock)
        return fillOwnStock(produced, ownStock, amount)
    }

    // ------------------------------------------------------------
    // 折叠分解
    // ------------------------------------------------------------

    /**
     * 折叠一个"纯生产需求" [toProduce]（目标库存已清零），返回生产导向的解。
     */
    private fun foldPlan(toProduce: Long, prodStock: Array<BigInteger>): Solve {
        if (toProduce <= 0) return emptySolve()
        val mult = toProduce / FOLD_BASE
        val rem = toProduce % FOLD_BASE
        if (mult <= 0) return exactPlan(toProduce, prodStock)

        val base = exactPlan(FOLD_BASE, prodStock)
        if (base.simulation) return base

        var k = mult
        for (i in 0 until nItems) {
            if (base.net[i] > BigInteger.ZERO) {
                val allowed = prodStock[i] / base.net[i]
                if (allowed.signum() > 0 && allowed < BigInteger.valueOf(k)) k = allowed.toLong()
            }
        }
        k = max(1, k)

        if (k >= mult) {
            val scaled = scale(base, mult)
            val remStock = Array(nItems) { i -> prodStock[i] - base.net[i] * BigInteger.valueOf(mult) }
            val remPlan = if (rem > 0) exactPlan(rem, remStock) else emptySolve()
            return merge(scaled, remPlan, toProduce)
        }

        // 库存不足以放满 mult 倍：放满 k 倍，剩余部分最大化产出一次收尾
        val scaled = scale(base, k)
        val restStock = Array(nItems) { i -> prodStock[i] - base.net[i] * BigInteger.valueOf(k) }
        val rest = toProduce - k * FOLD_BASE
        val maxPlan = if (rest > 0) exactPlanMaxProduce(rest, restStock) else emptySolve()
        return merge(scaled, maxPlan, toProduce)
    }

    /** 模式解按 k 倍放大（xs/finalAmount/net 全部 ×k，BigInteger 饱和保护）。 */
    private fun scale(base: Solve, k: Long): Solve {
        val kb = BigInteger.valueOf(k)
        fun times(v: Long): Long =
            BigInteger.valueOf(v).multiply(kb)
                .min(Long.MAX_VALUE.toBigInteger())
                .toLong()

        return Solve(
            xs = LongArray(mRecipes) { r -> times(base.xs[r]) },
            finalAmount = times(base.finalAmount),
            missingOutput = 0,
            net = Array(nItems) { i -> base.net[i] * kb },
            simulation = false,
        )
    }

    /** 合并折叠计划与余数计划，缺货量按 [amount] 重新核算。 */
    private fun merge(a: Solve, b: Solve, amount: Long): Solve {
        val finalAmount = minOf(amount, a.finalAmount + b.finalAmount)
        return Solve(
            xs = LongArray(mRecipes) { r -> a.xs[r] + b.xs[r] },
            finalAmount = finalAmount,
            missingOutput = (amount - finalAmount).coerceAtLeast(0L),
            net = Array(nItems) { i -> a.net[i] + b.net[i] },
            simulation = a.simulation || b.simulation,
        )
    }

    /** 把目标物品的一次性库存回填到折叠结果：最终交付 = min(amount, ownStock + 生产量)。 */
    private fun fillOwnStock(plan: Solve, ownStock: Long, amount: Long): Solve {
        val finalAmount = minOf(amount, ownStock + plan.finalAmount)
        return Solve(
            xs = plan.xs,
            finalAmount = finalAmount,
            missingOutput = (amount - finalAmount).coerceAtLeast(0L),
            net = plan.net,
            simulation = plan.simulation,
        )
    }

    // ------------------------------------------------------------
    // 精确域（两阶段 MIP）
    // ------------------------------------------------------------

    private fun exactPlan(amount: Long, stockBig: Array<BigInteger>): Solve {
        val stockCap = capStock(stockBig)
        solveStage1(stockCap, amount)?.let { xs ->
            if (validate(xs, stockCap)) return buildSolve(xs, stage2 = false, stockBig, amount)
        }
        val xs = solveStage2(stockCap)
        if (xs != null && validate(xs, stockCap)) {
            return buildSolve(xs, stage2 = true, stockBig, amount)
        }
        return missingSolve(amount)
    }

    private fun exactPlanMaxProduce(amount: Long, stockBig: Array<BigInteger>): Solve {
        val stockCap = capStock(stockBig)
        val xs = solveStage2(stockCap)
        if (xs != null && validate(xs, stockCap)) {
            return buildSolve(xs, stage2 = true, stockBig, amount)
        }
        return missingSolve(amount)
    }

    private fun capStock(stockBig: Array<BigInteger>): DoubleArray =
        DoubleArray(nItems) { i ->
            minOf(stockBig[i].min(MAX_EXACT.toBigInteger()).toDouble(), MAX_EXACT.toDouble())
        }

    private fun solveStage1(stockCap: DoubleArray, amount: Long): LongArray? {
        val model = ExpressionsBasedModel()
        model.options.time_suffice = TIME_SUFFICE_MS
        model.options.time_abort = TIME_ABORT_MS
        val vars = ArrayList<Variable>(mRecipes)
        for (r in 0 until mRecipes) {
            vars.add(
                model.addVariable("x$r")
                    .integer(true)
                    .lower(0L)
                    .upper(MAX_VAR.toDouble())
                    .weight(if (isEmitter[r]) 0.0 else 1.0)
            )
        }
        addStockConstraints(model, vars, stockCap)
        val target = model.addExpression("target")
        for ((r, delta) in deltaByItem[targetId]) target.set(vars[r], delta)
        target.lower((amount - stockCap[targetId].roundToLong()).coerceAtLeast(0L).toDouble())

        val result = model.minimise()
        if (!result.state.isFeasible) return null
        return extractSolution(vars)
    }

    private fun solveStage2(stockCap: DoubleArray): LongArray? {
        val model = ExpressionsBasedModel()
        model.options.time_suffice = TIME_SUFFICE_MS
        model.options.time_abort = TIME_ABORT_MS
        val vars = ArrayList<Variable>(mRecipes)
        for (r in 0 until mRecipes) {
            val v = model.addVariable("x$r")
                .integer(true)
                .lower(0L)
                .upper(MAX_VAR.toDouble())
            if (isEmitter[r]) v.upper(0L)
            vars.add(v)
        }
        addStockConstraints(model, vars, stockCap)
        val objective = model.addExpression("objective")
        for ((r, delta) in deltaByItem[targetId]) if (delta > 0) objective.set(vars[r], delta)
        objective.weight(1.0)

        val result = model.maximise()
        if (!result.state.isFeasible) return null
        return extractSolution(vars)
    }

    private fun addStockConstraints(model: ExpressionsBasedModel, vars: List<Variable>, stockCap: DoubleArray) {
        for (i in 0 until nItems) {
            val deltas = deltaByItem[i]
            if (deltas.isEmpty()) continue
            val expr = model.addExpression("stock$i")
            for ((r, delta) in deltas) expr.set(vars[r], delta)
            // 库存守恒：库存 + SUM(delta*x) >= 0
            expr.lower(-stockCap[i])
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
    private fun validate(xs: LongArray, stockCap: DoubleArray): Boolean {
        for (i in 0 until nItems) {
            val deltas = deltaByItem[i]
            if (deltas.isEmpty()) continue
            var sum = 0.0
            for ((r, delta) in deltas) sum += delta * xs[r]
            if (sum < -stockCap[i] - 1.0) return false
        }
        return true
    }

    /** 由解组装 [Solve]：净消耗、最终交付量、目标缺货、子域库存不足标记。 */
    private fun buildSolve(xs: LongArray, stage2: Boolean, stockBig: Array<BigInteger>, amount: Long): Solve {
        val net = netConsumption(xs)
        var finalAmount = amount
        var missingOutput = 0L
        if (stage2) {
            var produced = 0.0
            for ((r, delta) in deltaByItem[targetId]) if (delta > 0) produced += delta * xs[r]
            val reachable = minOf(amount, stockBig[targetId].min(MAX_EXACT.toBigInteger()).toLong() + produced.roundToLong())
            finalAmount = reachable
            missingOutput = (amount - reachable).coerceAtLeast(0L)
        }
        var inventoryShort = false
        for (i in 0 until nItems) {
            if (net[i] > stockBig[i]) {
                inventoryShort = true
                break
            }
        }
        return Solve(
            xs = xs,
            finalAmount = finalAmount,
            missingOutput = missingOutput,
            net = net,
            simulation = stage2 || inventoryShort,
        )
    }

    private fun netConsumption(xs: LongArray): Array<BigInteger> {
        val cons = Array(nItems) { BigInteger.ZERO }
        for (i in 0 until nItems) {
            val deltas = deltaByItem[i]
            if (deltas.isEmpty()) continue
            var s = BigInteger.ZERO
            for ((r, delta) in deltas) {
                if (delta != 0L) s += delta.toBigInteger() * xs[r].toBigInteger()
            }
            if (s.signum() < 0) cons[i] = s.negate()
        }
        return cons
    }

    private fun emptySolve(): Solve = Solve(
        xs = LongArray(mRecipes),
        finalAmount = 0,
        missingOutput = 0,
        net = Array(nItems) { BigInteger.ZERO },
        simulation = false,
    )

    private fun missingSolve(amount: Long): Solve = Solve(
        xs = LongArray(mRecipes),
        finalAmount = 0,
        missingOutput = amount,
        net = Array(nItems) { BigInteger.ZERO },
        simulation = true,
    )
}
