package averith.planner

import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 纯数值规划核心 [MipPlanner] 的回归测试。
 *
 * 覆盖两个已知 bug：
 * 1. **ojalgo 大库存数值不稳定**：整数 MIP 在库存 RHS ≥ 2^32 量级时假 INFEASIBLE，
 *    修复方式是给整数变量加 [MipPlanner.MAX_VAR] 上界。
 * 2. **折叠域幽灵基数**：目标物品自身库存被折叠放大重复计数，零合成就谎报全额交付。
 */
class MipPlannerTest {
    private fun graph(
        itemCount: Int,
        deltas: List<List<Pair<Int, Long>>>,
        targetId: Int,
        emitters: Set<Int> = emptySet(),
        stock: Array<BigInteger>,
    ): PlanGraph<Int> {
        val recipes = deltas.mapIndexed { r, recipeDeltas ->
            PlanRecipe(
                -1,
                recipeDeltas.filter { it.second < 0 }.map { ItemRef(it.first, -it.second) },
                recipeDeltas.filter { it.second > 0 }.map { ItemRef(it.first, it.second) },
                emitter = r in emitters,
            )
        }
        return PlanGraph(stock, targetId, recipes)
    }

    /**
     * 直接求解：构建图 + [MipPlanner.plan]。
     *
     * [deltas] 每项为 (物品id, 净变化)，负 = 消耗、正 = 产出。
     */
    private fun solve(
        itemCount: Int,
        deltas: List<List<Pair<Int, Long>>>,
        targetId: Int,
        emitters: Set<Int> = emptySet(),
        amount: Long,
        stock: Array<BigInteger>,
    ): MipPlanner.Solve = MipPlanner(graph(itemCount, deltas, targetId, emitters, stock)).plan(amount)

    /** 单配方：in → out，1:1。 */
    private fun oneToOne(targetId: Int = 1) =
        listOf(listOf(0 to -1L, 1 to 1L))

    private fun stock(vararg v: Long) = Array(v.size) { i -> BigInteger.valueOf(v[i]) }

    // ------------------------------------------------------------
    // ojalgo 大库存数值防护（MAX_VAR 上界）
    // ------------------------------------------------------------

    @Test
    fun hugeInputStockExactPath() {
        val s = solve(2, oneToOne(), 1, amount = 100, stock = stock(1_000_000_000_000_000L, 0L))
        assertEquals(100L, s.finalAmount)
        assertEquals(0L, s.missingOutput)
        assertEquals(100L, s.xs[0])
        assertEquals(BigInteger.valueOf(100), s.net[0])
        assertFalse(s.simulation)
    }

    @Test
    fun hugeStockAtDoubleLimit() {
        val s = solve(2, oneToOne(), 1, amount = 100, stock = stock((1L shl 53) + 1L, 0L))
        assertEquals(100L, s.finalAmount)
        assertEquals(100L, s.xs[0])
        assertEquals(0L, s.missingOutput)
    }

    @Test
    fun hugeInputStockMultiInputRecipe() {
        val s = solve(2, listOf(listOf(0 to -2L, 1 to 1L)), 1, amount = 100, stock = stock(1_000_000_000_000_000L, 0L))
        assertEquals(100L, s.finalAmount)
        assertEquals(100L, s.xs[0])
        assertEquals(BigInteger.valueOf(200), s.net[0])
    }

    @Test
    fun chainRecipeHugeStock() {
        val s = solve(
            3,
            listOf(listOf(0 to -1L, 1 to 1L), listOf(1 to -1L, 2 to 1L)),
            targetId = 2,
            amount = 1000,
            stock = stock(1_000_000_000_000_000L, 0L, 0L),
        )
        assertEquals(1000L, s.finalAmount)
        assertEquals(1000L, s.xs[0])
        assertEquals(1000L, s.xs[1])
        assertEquals(0L, s.missingOutput)
    }

    // ------------------------------------------------------------
    // CRAFT_LESS：库存不足时最大化产出
    // ------------------------------------------------------------

    @Test
    fun shortStockProducesLess() {
        val s = solve(2, oneToOne(), 1, amount = 100, stock = stock(50L, 0L))
        assertEquals(50L, s.finalAmount)
        assertEquals(50L, s.missingOutput)
        assertEquals(50L, s.xs[0])
        assertTrue(s.simulation)
    }

    // ------------------------------------------------------------
    // 折叠域：目标物品自身库存一次性记账（幽灵基数修复）
    // ------------------------------------------------------------

    @Test
    fun foldingCountsOwnStockOnce() {
        val request = 10L * MipPlanner.FOLD_BASE
        // 目标库存 3×FOLD_BASE：只应生产 7×FOLD_BASE，不能零合成就谎报全额交付
        val s = solve(2, oneToOne(), 1, amount = request, stock = stock(1_000_000_000_000_000L, 3L * MipPlanner.FOLD_BASE))
        assertEquals(request, s.finalAmount)
        assertEquals(0L, s.missingOutput)
        assertEquals(7L * MipPlanner.FOLD_BASE, s.xs[0])
        assertEquals(BigInteger.valueOf(7L * MipPlanner.FOLD_BASE), s.net[0])
        assertFalse(s.simulation)
    }

    @Test
    fun foldingSatisfiedEntirelyByTargetStock() {
        val request = 5L * MipPlanner.FOLD_BASE
        val s = solve(2, oneToOne(), 1, amount = request, stock = stock(0L, 10L * MipPlanner.FOLD_BASE))
        assertEquals(request, s.finalAmount)
        assertEquals(0L, s.missingOutput)
        assertEquals(0L, s.xs[0])
        assertEquals(BigInteger.ZERO, s.net[0])
        assertFalse(s.simulation)
    }

    @Test
    fun foldingInsufficientRawMaterial() {
        val request = 10L * MipPlanner.FOLD_BASE
        // 原料只能支撑 2 倍折叠：交付 2×FOLD_BASE，其余缺货
        val s = solve(2, oneToOne(), 1, amount = request, stock = stock(2L * MipPlanner.FOLD_BASE, 0L))
        assertEquals(2L * MipPlanner.FOLD_BASE, s.finalAmount)
        assertEquals(8L * MipPlanner.FOLD_BASE, s.missingOutput)
        assertEquals(2L * MipPlanner.FOLD_BASE, s.xs[0])
        assertTrue(s.simulation)
    }

    @Test
    fun foldingRemainderExactPlan() {
        val request = (10L * MipPlanner.FOLD_BASE) + 1234
        val s = solve(2, oneToOne(), 1, amount = request, stock = stock(1_000_000_000_000_000L, 0L))
        assertEquals(request, s.finalAmount)
        assertEquals(0L, s.missingOutput)
        assertEquals(request, s.xs[0])
        assertFalse(s.simulation)
    }
}
