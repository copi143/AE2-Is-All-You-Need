package allyouneed.logic.crafting

import allyouneed.api.BigStackSource
import allyouneed.util.bigint.BigKeyCounter
import allyouneed.util.bigint.BigStack
import appeng.api.crafting.IPatternDetails
import appeng.api.networking.IGrid
import appeng.api.stacks.AEKey
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * - [goal] 最终的合成目标
 */
class CraftingInventorySnapshot(grid: IGrid, val goal: BigStack) {
    val keyIndex = Object2IntOpenHashMap<AEKey>()
    val resources = ArrayList<Resource>()
    val patterns = ArrayList<IPatternDetails>()
    val recipeIndex = Object2IntOpenHashMap<Pair<List<Int>, List<Int>>>()
    val recipes = ArrayList<Recipe>()

    private class Solver(grid: IGrid) {
        val cs = grid.craftingService
        val ss = grid.storageService
        val counter = BigKeyCounter().apply {
            if (!BigStackSource.collectBigStacks(ss.inventory, this)) {
                this.addAll(ss.cachedInventory)
            }
        }
    }

    private fun Solver.addPattern(pattern: IPatternDetails) {
        val id = patterns.size
        patterns.add(pattern)
        for (source in pattern.inputs) {
            val key = source.possibleInputs[0].what.dropSecondary()
            val usedKey = source.getRemainingKey(key)
            if (usedKey != null) {
                usedKey == key
            }
//            add(source)
        }
        for (target in pattern.outputs) {
            add(target.what)
        }
    }

    private fun Solver.addRecipe(pattern: Int, sources: ArrayList<Int>, targets: ArrayList<Int>): Recipe {
        val r = recipeIndex.getOrDefault(Pair(sources, targets), -1)
        if (r >= 0) {
            val recipe = recipes[r]
            if (pattern >= 0) recipe.pattern.add(pattern)
            return recipe
        }
        val id = recipes.size
        val recipe = Recipe(id, ArrayList(), sources, targets)
        recipes.add(recipe)
        recipeIndex[Pair(sources, targets)] = id
        recipe.pattern.add(pattern)
        return recipe
    }

    private fun Solver.add(key: AEKey) {
        keyIndex.getOrDefault(key, -1) < 0 || return
        val id = resources.size
        resources.add(Resource(BigStack(key, counter.get(key))))
        keyIndex[key] = id
        if (cs.canEmitFor(key)) {
            addRecipe(-1, emptySource, arrayListOf(id))
        } else for (pattern in cs.getCraftingFor(key)) {
            addPattern(pattern)
        }
    }

    init {
        Solver(grid).add(goal.key)
    }

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
        val catalysts: ArrayList<Int> = ArrayList(),
    )

    class Resource(val stack: BigStack, val recipes: ArrayList<Recipe> = ArrayList())

    companion object {
        private val emptySource = ArrayList<Int>()
    }
}
