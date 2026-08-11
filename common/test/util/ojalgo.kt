package allyouneed.util

import org.ojalgo.optimisation.ExpressionsBasedModel
import org.ojalgo.optimisation.Optimisation
import org.ojalgo.optimisation.Variable
import kotlin.math.max

// ------------------------------------------------------------
// Domain model
// ------------------------------------------------------------

data class Recipe(
    val id: Int,
    val name: String,

    /**
     * Positive = output
     * Negative = input
     *
     * Example:
     * 2 Iron + 1 Coal -> 1 Steel
     *
     * delta = mapOf(
     *     "Iron" to -2L,
     *     "Coal" to -1L,
     *     "Steel" to 1L
     * )
     */
    val delta: Map<Int, Long>,

    /**
     * Optional production time / cost.
     * Used only as a lower-priority objective.
     */
    val time: Long = 1L,

    /**
     * Maximum number of batches.
     *
     * IMPORTANT:
     * For normal recipes this can be derived from input inventory.
     * For positive production cycles it may not be naturally bounded,
     * so a finite cap is required unless another physical constraint
     * exists (machine count, energy, time, storage, etc.).
     */
    val maxBatches: Long = Long.MAX_VALUE
)

data class ProductionProblem(
    val inventory: Map<Int, Long>,
    val recipes: List<Recipe>,
    val targetItem: Int,
    val targetAmount: Long
)

data class ProductionPlan(
    val batches: Map<Int, Long>,
    val finalInventory: Map<Int, Long>,
    val objectiveValue: Double
)

// ------------------------------------------------------------
// Solver
// ------------------------------------------------------------

class ProductionMilpSolver(
    private val maxDefaultBatches: Long = 1_000_000L
) {

    fun solve(problem: ProductionProblem): ProductionPlan? {

        require(problem.targetAmount >= 0)

        // ----------------------------------------------------
        // 1. Remove recipes that cannot possibly contribute
        //    to the target.
        //
        // This is deliberately separated from the MILP.
        // The MILP should only see the relevant subgraph.
        // ----------------------------------------------------
        val relevantRecipes = findRelevantRecipes(problem)

        if (relevantRecipes.isEmpty()) {
            val current = problem.inventory[problem.targetItem] ?: 0L

            return if (current >= problem.targetAmount) {
                ProductionPlan(
                    batches = emptyMap(),
                    finalInventory = problem.inventory,
                    objectiveValue = 0.0
                )
            } else {
                null
            }
        }

        // ----------------------------------------------------
        // 2. Remove Pareto-dominated recipes.
        // ----------------------------------------------------
        val prunedRecipes = removeDominatedRecipes(
            relevantRecipes,
            problem.targetItem
        )

        // ----------------------------------------------------
        // 3. Build MILP
        // ----------------------------------------------------
        val model = ExpressionsBasedModel()

        val variables = ArrayList<Variable>(prunedRecipes.size)

        for (recipe in prunedRecipes) {

            val upperBound =
                if (recipe.maxBatches != Long.MAX_VALUE) {
                    recipe.maxBatches
                } else {
                    deriveSafeUpperBound(recipe, problem)
                }

            val variable = model
                .addVariable("r_${recipe.id}")
                .integer()
                .lower(0L)
                .upper(upperBound.toDouble())

            variables += variable
        }

        // ----------------------------------------------------
        // 4. Inventory constraints
        //
        // inventory[i] + SUM(delta[r][i] * x[r]) >= 0
        //
        // => SUM(delta[r][i] * x[r]) >= -inventory[i]
        // ----------------------------------------------------

        val allItems = HashSet<Int>()

        allItems += problem.inventory.keys

        for (recipe in prunedRecipes) {
            allItems += recipe.delta.keys
        }

        for (item in allItems) {

            val constraint = model.addExpression("stock_$item")

            for ((index, recipe) in prunedRecipes.withIndex()) {
                val coefficient = recipe.delta[item] ?: 0L

                if (coefficient != 0L) {
                    constraint.set(
                        variables[index],
                        coefficient
                    )
                }
            }

            val initialStock = problem.inventory[item] ?: 0L

            // initialStock + delta >= 0
            constraint.lower(-initialStock.toDouble())
        }

        // ----------------------------------------------------
        // 5. Target constraint
        //
        // inventory[target] + SUM(delta[target] * x) >= target
        //
        // => SUM(delta[target] * x)
        //       >= target - inventory[target]
        // ----------------------------------------------------

        val initialTarget =
            problem.inventory[problem.targetItem] ?: 0L

        val targetConstraint =
            model.addExpression("TARGET")

        for ((index, recipe) in prunedRecipes.withIndex()) {
            val coefficient =
                recipe.delta[problem.targetItem] ?: 0L

            if (coefficient != 0L) {
                targetConstraint.set(
                    variables[index],
                    coefficient
                )
            }
        }

        targetConstraint.lower(
            (problem.targetAmount - initialTarget).toDouble()
        )

        // ----------------------------------------------------
        // 6. Objective
        //
        // Minimize:
        //
        //   A * external consumption
        // + B * production time
        // + C * total batches
        //
        // For the first implementation we use:
        //
        //     total batches
        //
        // as the primary objective.
        //
        // You can replace this with inventory scarcity weights.
        // ----------------------------------------------------

        val objective = model.addExpression("objective")

        for ((index, recipe) in prunedRecipes.withIndex()) {

            /*
             * A small positive coefficient prevents the solver
             * from arbitrarily producing useless extra material.
             */
            val coefficient =
                1.0 +
                        recipe.time.toDouble() * 1e-6

            objective.set(
                variables[index],
                coefficient
            )
        }

        objective.weight(1.0)

        // ----------------------------------------------------
        // 7. Solve
        // ----------------------------------------------------

        val result = model.minimise()

        if (!result.state.isFeasible) {
            return null
        }

        // ----------------------------------------------------
        // 8. Extract integer solution
        // ----------------------------------------------------

        val batches = HashMap<Int, Long>()

        for ((index, recipe) in prunedRecipes.withIndex()) {

            val value = variables[index].value

            if (value != null) {

                // Round only at the extraction boundary.
                // The variable itself is integer-constrained.
                val count = value.toLong()

                if (count > 0L) {
                    batches[recipe.id] = count
                }
            }
        }

        // ----------------------------------------------------
        // 9. Calculate final inventory independently.
        //
        // Never rely only on solver variable values when
        // constructing the actual production plan.
        // ----------------------------------------------------

        val finalInventory =
            problem.inventory.toMutableMap()

        for (recipe in prunedRecipes) {

            val count = batches[recipe.id] ?: 0L

            if (count == 0L) continue

            for ((item, delta) in recipe.delta) {

                finalInventory[item] =
                    (finalInventory[item] ?: 0L) +
                            delta * count
            }
        }

        return ProductionPlan(
            batches = batches,
            finalInventory = finalInventory,
            objectiveValue = result.value.toDouble()
        )
    }

    // --------------------------------------------------------
    // Relevant recipe pruning
    // --------------------------------------------------------

    private fun findRelevantRecipes(
        problem: ProductionProblem
    ): List<Recipe> {

        val producers = HashMap<Int, MutableList<Recipe>>()

        for (recipe in problem.recipes) {

            for ((item, delta) in recipe.delta) {

                if (delta > 0) {
                    producers
                        .getOrPut(item) { ArrayList() }
                        .add(recipe)
                }
            }
        }

        /*
         * Reverse reachability:
         *
         * target
         *   <- recipes producing target
         *   <- their inputs
         *   <- recipes producing those inputs
         *   ...
         */

        val requiredItems = HashSet<Int>()
        val queue = ArrayDeque<Int>()

        requiredItems += problem.targetItem
        queue.add(problem.targetItem)

        val relevant = LinkedHashSet<Recipe>()

        while (queue.isNotEmpty()) {

            val item = queue.removeFirst()

            for (recipe in producers[item].orEmpty()) {

                if (!relevant.add(recipe)) continue

                for ((input, delta) in recipe.delta) {

                    if (delta < 0) {
                        if (requiredItems.add(input)) {
                            queue.add(input)
                        }
                    }
                }
            }
        }

        return relevant.toList()
    }

    // --------------------------------------------------------
    // Pareto dominance
    // --------------------------------------------------------

    private fun removeDominatedRecipes(
        recipes: List<Recipe>,
        targetItem: Int
    ): List<Recipe> {

        /*
         * This is intentionally conservative.
         *
         * Recipe A dominates B when:
         *
         *   for every consumed item:
         *       A consumes <= B
         *
         *   for every produced item:
         *       A produces >= B
         *
         * and at least one inequality is strict.
         *
         * This means B can never be useful if both recipes
         * are available in exactly the same context.
         */

        val result = ArrayList<Recipe>()

        outer@ for (candidate in recipes) {

            for (other in recipes) {

                if (candidate === other) continue

                if (dominates(other, candidate)) {
                    continue@outer
                }
            }

            result += candidate
        }

        return result
    }

    private fun dominates(
        a: Recipe,
        b: Recipe
    ): Boolean {

        val items =
            HashSet<Int>().apply {
                addAll(a.delta.keys)
                addAll(b.delta.keys)
            }

        var strictlyBetter = false

        for (item in items) {

            val da = a.delta[item] ?: 0L
            val db = b.delta[item] ?: 0L

            /*
             * Compare net resource vectors.
             *
             * For each item, larger delta is better.
             *
             * Example:
             *
             * A = -10 Iron + 5 Steel
             * B = -10 Iron + 3 Steel
             *
             * A dominates B.
             */
            if (da < db) {
                return false
            }

            if (da > db) {
                strictlyBetter = true
            }
        }

        return strictlyBetter
    }

    // --------------------------------------------------------
    // Upper bound
    // --------------------------------------------------------

    private fun deriveSafeUpperBound(
        recipe: Recipe,
        problem: ProductionProblem
    ): Long {

        /*
         * If this recipe consumes something from the initial
         * inventory, derive a bound from that resource.
         *
         * If it consumes nothing, it may be part of a positive
         * cycle. Such a variable cannot be naturally bounded
         * by inventory alone.
         *
         * Therefore use a configurable cap.
         */

        var bound = maxDefaultBatches

        for ((item, delta) in recipe.delta) {

            if (delta >= 0L) continue

            val stock = problem.inventory[item] ?: 0L

            val maxByThisInput =
                stock / -delta

            bound = minOf(bound, maxByThisInput)
        }

        return max(0L, bound)
    }
}
