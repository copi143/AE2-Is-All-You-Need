package averith.planner

import java.math.BigInteger

/**
 * 通用合成规划问题：一张"物品 ↔ 配方"二分图，与任何具体游戏/模组类型解耦。
 *
 * 物品用连续的 [Int] id 表示（0..[stock].size-1），调用方自行维护 id 与真实物品
 * （如 AEKey）之间的映射；配方通过 [PlanRecipe.payload] 携带调用方自定义的负载
 * （如样板索引），求解后原样回映射。
 *
 * @param stock 各物品初始库存，[BigInteger] 精确表示，不允许为负。
 * @param target 目标物品 id。
 * @param recipes 可用配方列表，顺序即求解结果 [MipPlanner.Solve.xs] 的下标。
 */
class PlanGraph<T>(
    val stock: Array<BigInteger>,
    val target: Int,
    val recipes: List<PlanRecipe<T>>,
)

/**
 * 一条通用配方：把 [sources] 中的物品按量消耗，产出 [targets]。
 *
 * @param payload 调用方自定义负载，求解时不被解释，原样返回。
 * @param sources 消耗项（正 = 消耗）。
 * @param targets 产出项（正 = 产出）。
 * @param catalysts 催化剂：不进入库存约束，仅供调用方做损耗后处理。
 * @param emitter 发射型配方：无实际消耗来源（如 AE 的 emit），求解时不参与
 *   最小化、在"最大化产出"阶段被固定为 0。数量通过 [targets] 照常建模。
 */
class PlanRecipe<T>(
    val payload: T,
    val sources: List<ItemRef>,
    val targets: List<ItemRef>,
    val catalysts: List<CatalystRef> = emptyList(),
    val emitter: Boolean = false,
)

/**
 * 配方中一个物品的引用，携带数量。
 */
@JvmRecord
data class ItemRef(val id: Int, val amount: Long)

/**
 * 催化剂引用。[lossy] 为 true 表示慢耗型（如耐久工具），false 表示完全不消耗。
 */
@JvmRecord
data class CatalystRef(val id: Int, val amount: Long, val lossy: Boolean)

/**
 * 配方内容键：用于按"实际消耗/产出/催化剂组合"去重等价配方。
 */
@JvmRecord
data class RecipeKey(
    val sources: List<ItemRef>,
    val targets: List<ItemRef>,
    val catalysts: List<CatalystRef>,
)
