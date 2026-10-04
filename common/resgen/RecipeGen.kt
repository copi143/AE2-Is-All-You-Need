package allyouneed.resgen

import java.nio.file.Path

// Recipes: housing, components, cells (AE2 parity, extrapolated to 256T).
fun generateRecipes(dataOutput: Path, modId: String) {
    fun housingMetal(type: String) =
        if (type == "fluid") "minecraft:copper_ingot" else "minecraft:iron_ingot"

    recipeGen(modId, dataOutput) {
        for (type in storageCellGroups.keys) {
            shaped("${type}_cell_housing") {
                pattern("aba", "b b", "ccc")
                key('a', "ae2:quartz_glass")
                key('b', "minecraft:redstone")
                key('c', housingMetal(type))
                unlock("has_redstone", "minecraft:redstone")
            }
        }

        for ((idx, tier) in tiers.withIndex()) {
            shaped("cell_component_${tier.lowercase()}") {
                if (idx == 0) {
                    pattern("aba", "bcb", "aba")
                    key('a', "minecraft:redstone")
                    key('b', "ae2:certus_quartz_crystal")
                    key('c', "ae2:logic_processor")
                    unlock("has_logic_processor", "ae2:logic_processor")
                } else {
                    val previous = "cell_component_${tiers[idx - 1].lowercase()}"
                    val material = when (idx) {
                        1 -> "minecraft:redstone"
                        2, 3 -> "minecraft:glowstone_dust"
                        else -> "ae2:sky_dust"
                    }
                    pattern("aba", "cdc", "aca")
                    key('a', material)
                    key('b', "ae2:calculation_processor")
                    key('c', previous)
                    key('d', "ae2:quartz_glass")
                    unlock(if (idx == 1) "has_cell_component_1k" else "has_prev", previous)
                }
            }
        }

        for ((type, cells) in storageCellGroups) {
            for ((idx, cell) in cells.withIndex()) {
                val component = "cell_component_${tiers[idx].lowercase()}"
                shaped(cell.id) {
                    pattern("aba", "bcb", "ddd")
                    key('a', "ae2:quartz_glass")
                    key('b', "minecraft:redstone")
                    key('c', component)
                    key('d', housingMetal(type))
                    unlock("has_component", component)
                }
                shapeless("${cell.id}_storage", result = cell.id) {
                    ingredient("${type}_cell_housing")
                    ingredient(component)
                    unlock("has_component", component)
                }
            }
        }
    }
    println("[recipes] generated housing + ${tiers.size} components + ${storageCellGroups.values.sumOf { it.size } * 2} cell recipes")
}
