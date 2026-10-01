package allyouneed.logic.crafting

import allyouneed.util.bigint.BigStack
import allyouneed.util.debugLogger
import allyouneed.util.logger
import appeng.api.crafting.IPatternDetails
import appeng.api.networking.IGrid
import appeng.api.stacks.AEKey
import averith.planner.CatalystRef
import averith.planner.ItemRef
import averith.planner.PlanGraph
import averith.planner.PlanRecipe
import averith.planner.RecipeKey
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.world.level.Level

/**
 * 从 AE 网络快照构建通用规划图 [graph]：
 * 物品 id ↔ [AEKey] 的映射由本类维护，配方负载为 [IPatternDetails] 在 [patterns] 中的
 * 下标（发射配方为 -1）。
 *
 * - [goal] 最终的合成目标
 */
class CraftingInventorySnapshot(level: Level, grid: IGrid, val goal: BigStack) {
    val keyIndex = Object2IntOpenHashMap<AEKey>()
    val resources = ArrayList<Resource>()
    val patterns = ArrayList<IPatternDetails>()
    val recipeIndex = Object2IntOpenHashMap<RecipeKey>()
    val recipes = ArrayList<PlanRecipe<Int>>()
    val multiplePaths: Boolean

    /** 构建完成的通用规划图，可直接交给 averith.planner.MipPlanner。 */
    val graph: PlanGraph<Int>

    private class Solver(val level: Level, val grid: IGrid) {
        val snapshot = InventorySnapshot(grid)
    }

    private fun Solver.addPattern(resource: Resource, pattern: IPatternDetails) {
        val pr = PatternRecipe.fuzzy(level, snapshot, pattern)
        if (pr.isEmpty()) {
            debugLogger.info("addPattern $pattern: no usable fuzzy variant")
            return
        }
        debugLogger.info("addPattern $pattern")
        val id = patterns.size
        patterns.add(pattern)
        for (r in pr) {
            debugLogger.info("    $r")
            val recipeId = addRecipe(
                id,
                r.sources.mapTo(ArrayList()) { ItemRef(addKey(it.what).id, it.amount) },
                r.targets.mapTo(ArrayList()) { ItemRef(addKey(it.what).id, it.amount) },
                r.catalysts.mapTo(ArrayList()) { CatalystRef(addKey(it.stack.what).id, it.stack.amount, it.lossy) },
            )
            if (recipeId !in resource.recipeIds) {
                resource.recipeIds.add(recipeId)
            }
        }
    }

    /**
     * 按 [RecipeKey] 去重等价配方。
     *
     * 与旧实现一致：重复配方只保留首个负载（首个 [IPatternDetails] 下标），
     * 后续相同内容的样板被合并、不再单独出现。
     */
    private fun Solver.addRecipe(
        pattern: Int,
        sources: ArrayList<ItemRef>,
        targets: ArrayList<ItemRef>,
        catalysts: ArrayList<CatalystRef>,
    ): Int {
        val key = RecipeKey(sources, targets, catalysts)
        val r = recipeIndex.getOrDefault(key, -1)
        if (r >= 0) return r
        debugLogger.info("addRecipe $sources $targets")
        val id = recipes.size
        recipes.add(PlanRecipe(pattern, sources, targets, catalysts, emitter = pattern < 0))
        recipeIndex[key] = id
        return id
    }

    private fun Solver.addKey(key: AEKey): Resource {
        val k = keyIndex.getOrDefault(key, -1)
        if (k >= 0) return resources[k]
        debugLogger.info("addKey $key")
        val id = resources.size
        resources.add(Resource(id, BigStack(key, snapshot.stored.getCounter(key).toBigInteger())))
        keyIndex[key] = id
        if (grid.craftingService.canEmitFor(key)) {
            addRecipe(-1, arrayListOf(), arrayListOf(ItemRef(id, 1)), arrayListOf())
        } else for (pattern in grid.craftingService.getCraftingFor(key)) {
            addPattern(resources[id], pattern)
        }
        return resources[id]
    }

    init {
        Solver(level, grid).addKey(goal.key)
        multiplePaths = resources.any { it.recipeIds.size > 1 }
        graph = PlanGraph(
            Array(resources.size) { i -> resources[i].stack.valBig },
            keyIndex.getOrDefault(goal.key, -1),
            recipes,
        )
        logger.info(
            "CraftingInventorySnapshot: goal %s, %d items, %d recipes, %d patterns".format(
                goal, resources.size, recipes.size, patterns.size
            )
        )
    }

    class Resource(val id: Int, val stack: BigStack) {
        val recipeIds: HashSet<Int> = HashSet()
    }
}
