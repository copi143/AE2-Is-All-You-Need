package allyouneed.logic.crafting

import allyouneed.util.bigint.BigStack
import allyouneed.util.debugLogger
import allyouneed.util.logger
import appeng.api.crafting.IPatternDetails
import appeng.api.networking.IGrid
import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.world.level.Level
import java.util.TreeMap

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
            debugLogger.info("addPattern {}: no usable fuzzy variant", pattern)
            return
        }
        debugLogger.info("addPattern {}", pattern)
        val id = patterns.size
        patterns.add(pattern)
        for (r in pr) {
            debugLogger.info("    {}", r)
            val recipe = addRecipe(
                id,
                itemRefs(r.sources, true),
                itemRefs(r.targets, false),
                catalystRefs(r.catalysts),
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
            if (pattern >= 0 && pattern !in recipe.pattern) recipe.pattern.add(pattern)
            return recipe
        }
        debugLogger.info("addRecipe {} {}", sources, targets)
        val id = recipes.size
        val recipe = Recipe(id, ArrayList(), sources, targets, catalysts)
        recipes.add(recipe)
        recipeIndex[RecipeKey(sources, targets, catalysts)] = id
        recipe.pattern.add(pattern)
        return recipe
    }

    /** Canonicalise equivalent fuzzy permutations before recipe deduplication. */
    private fun Solver.itemRefs(stacks: List<GenericStack>, expandRecipes: Boolean): ArrayList<ItemRef> {
        val amounts = TreeMap<Int, Long>()
        for (stack in stacks) {
            val id = addKey(stack.what, expandRecipes).id
            amounts.merge(id, stack.amount, Long::plus)
        }
        return amounts.entries.mapTo(ArrayList(amounts.size)) { ItemRef(it.key, it.value) }
    }

    private data class CatalystKey(val id: Int, val lossy: Boolean)

    private fun Solver.catalystRefs(catalysts: List<PatternRecipe.Catalyst>): ArrayList<CatalystRef> {
        val amounts = HashMap<CatalystKey, Long>()
        for (catalyst in catalysts) {
            val key = CatalystKey(addKey(catalyst.stack.what, false).id, catalyst.lossy)
            amounts.merge(key, catalyst.stack.amount, Long::plus)
        }
        return amounts.entries
            .sortedWith(compareBy({ it.key.id }, { it.key.lossy }))
            .mapTo(ArrayList(amounts.size)) { CatalystRef(it.key.id, it.value, it.key.lossy) }
    }

    /**
     * Register [key] and optionally expand recipes that produce it.
     *
     * Only consumed resources belong to the reverse dependency graph. Outputs and catalysts still
     * need stable ids, but expanding their producers adds recipes that cannot contribute to the
     * current target and makes the MIP substantially larger.
     */
    private fun Solver.addKey(key: AEKey, expandRecipes: Boolean): Resource {
        val k = keyIndex.getOrDefault(key, -1)
        val resource = if (k >= 0) {
            resources[k]
        } else {
            debugLogger.info("addKey {}", key)
            val id = resources.size
            Resource(id, BigStack(key, snapshot.stored[key])).also {
                resources.add(it)
                keyIndex[key] = id
            }
        }
        if (!expandRecipes || resource.recipesExpanded) return resource

        // Mark before descending so cyclic crafting graphs terminate immediately.
        resource.recipesExpanded = true
        if (grid.craftingService.canEmitFor(key)) {
            addRecipe(-1, arrayListOf(), arrayListOf(ItemRef(resource.id, 1)), arrayListOf())
        } else for (pattern in grid.craftingService.getCraftingFor(key)) {
            addPattern(resource, pattern)
        }
        return resource
    }

    init {
        Solver(level, grid).addKey(goal.key, true)
        logger.info(
            "CraftingInventorySnapshot: goal {}, {} items, {} recipes, {} patterns",
            goal,
            resources.size,
            recipes.size,
            patterns.size,
        )
    }

    class Resource(val id: Int, val stack: BigStack) {
        val recipes: ArrayList<Recipe> = ArrayList()
        val recipeIds: HashSet<Int> = HashSet()
        internal var recipesExpanded: Boolean = false
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
