package allyouneed.logic.crafting

import allyouneed.util.bigint.BigStack
import allyouneed.util.debugLogger
import allyouneed.util.logger
import appeng.api.crafting.IPatternDetails
import appeng.api.networking.IGrid
import appeng.api.stacks.AEKey
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.world.level.Level

/**
 * - [goal] 最终的合成目标
 */
class CraftingInventorySnapshot(level: Level, grid: IGrid, val goal: BigStack) {
    val keyIndex = Object2IntOpenHashMap<AEKey>()
    val resources = ArrayList<Resource>()
    val patterns = ArrayList<IPatternDetails>()
    val recipeIndex = Object2IntOpenHashMap<RecipeKey>()
    val recipes = ArrayList<Recipe>()

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
            val recipe = addRecipe(
                id,
                r.sources.mapTo(ArrayList()) { ItemRef(addKey(it.what).id, it.amount) },
                r.targets.mapTo(ArrayList()) { ItemRef(addKey(it.what).id, it.amount) },
                r.catalysts.mapTo(ArrayList()) { CatalystRef(addKey(it.stack.what).id, it.stack.amount, it.lossy) },
            )
            if (recipe.id !in resource.recipeIds) {
                resource.recipeIds.add(recipe.id)
                resource.recipes.add(recipe)
            }
        }
    }

    private fun Solver.addRecipe(
        pattern: Int,
        sources: ArrayList<ItemRef>,
        targets: ArrayList<ItemRef>,
        catalysts: ArrayList<CatalystRef>,
    ): Recipe {
        val r = recipeIndex.getOrDefault(RecipeKey(sources, targets, catalysts), -1)
        if (r >= 0) {
            val recipe = recipes[r]
            if (pattern >= 0) recipe.pattern.add(pattern)
            return recipe
        }
        debugLogger.info("addRecipe $sources $targets")
        val id = recipes.size
        val recipe = Recipe(id, ArrayList(), sources, targets, catalysts)
        recipes.add(recipe)
        recipeIndex[RecipeKey(sources, targets, catalysts)] = id
        recipe.pattern.add(pattern)
        return recipe
    }

    private fun Solver.addKey(key: AEKey): Resource {
        val k = keyIndex.getOrDefault(key, -1)
        if (k >= 0) return resources[k]
        debugLogger.info("addKey $key")
        val id = resources.size
        resources.add(Resource(id, BigStack(key, snapshot.stored[key])))
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
        logger.info(
            "CraftingInventorySnapshot: goal %s, %d items, %d recipes, %d patterns".format(
                goal, resources.size, recipes.size, patterns.size
            )
        )
    }

    class Resource(val id: Int, val stack: BigStack) {
        val recipes: ArrayList<Recipe> = ArrayList()
        val recipeIds: HashSet<Int> = HashSet()
    }

    /**
     * 配方中一个物品的引用，携带数量。
     */
    data class ItemRef(val id: Int, val amount: Long)

    /**
     * 催化剂引用。 [lossy] 为 true 表示 [PatternRecipe.WTF.SlowlyConsumed]（慢耗工具），
     * false 表示 [PatternRecipe.WTF.Constant]（完全不消耗）。
     */
    data class CatalystRef(val id: Int, val amount: Long, val lossy: Boolean)

    data class RecipeKey(
        val sources: ArrayList<ItemRef>,
        val targets: ArrayList<ItemRef>,
        val catalysts: ArrayList<CatalystRef>,
    )

    /**
     * 由于 AE 的模糊匹配逻辑，一个 [IPatternDetails] 可以对应多个实际的 [Recipe]。
     *
     * 在我们的 [Recipe] 内不做模糊匹配，每个可用匹配写一份。
     *
     * 每个 [Recipe] 也可以对应多个 [IPatternDetails] 因为 AE 允许多个相同样板。
     */
    class Recipe(
        val id: Int,
        val pattern: ArrayList<Int>,
        val sources: ArrayList<ItemRef>,
        val targets: ArrayList<ItemRef>,
        val catalysts: ArrayList<CatalystRef>,
    )
}
