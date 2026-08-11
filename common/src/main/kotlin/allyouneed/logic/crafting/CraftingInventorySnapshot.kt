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
        println("PatternRecipe:")
        if (pr.isEmpty()) {
            println("    No recipe found")
            return
        }
        debugLogger.info("addPattern $pattern")
        val id = patterns.size
        patterns.add(pattern)
        for (r in pr) {
            println("    $r")
            val recipe = addRecipe(
                id,
                r.sources.mapTo(ArrayList()) { addKey(it.what).id },
                r.targets.mapTo(ArrayList()) { addKey(it.what).id },
                r.catalysts.mapTo(ArrayList()) { addKey(it.stack.what).id },
            )
            if (recipe.id !in resource.recipeIds) {
                resource.recipeIds.add(recipe.id)
                resource.recipes.add(recipe)
            }
        }
    }

    private fun Solver.addRecipe(
        pattern: Int,
        sources: ArrayList<Int>,
        targets: ArrayList<Int>,
        catalysts: ArrayList<Int>,
    ): Recipe {
        val r = recipeIndex.getOrDefault(Pair(sources, targets), -1)
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
            addRecipe(-1, arrayListOf(), arrayListOf(id), arrayListOf())
        } else for (pattern in grid.craftingService.getCraftingFor(key)) {
            addPattern(resources[id], pattern)
        }
        return resources[id]
    }

    init {
        logger.info("CraftingInventorySnapshot Testing")
        Solver(level, grid).addKey(goal.key)
        logger.info("CraftingInventorySnapshot End")
    }

    class Resource(val id: Int, val stack: BigStack) {
        val recipes: ArrayList<Recipe> = ArrayList()
        val recipeIds: HashSet<Int> = HashSet()
    }

    data class RecipeKey(
        val sources: ArrayList<Int>,
        val targets: ArrayList<Int>,
        val catalysts: ArrayList<Int>,
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
        val sources: ArrayList<Int>,
        val targets: ArrayList<Int>,
        val catalysts: ArrayList<Int>,
    )
}
