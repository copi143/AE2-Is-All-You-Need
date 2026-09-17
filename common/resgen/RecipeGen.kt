package allyouneed.resgen

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

// --- Recipes: housing, components, cells (AE2 parity, extrapolated to 256T) ---
fun generateRecipes(dataOutput: Path) {
    val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    fun criteriaFor(item: String): JsonObject = JsonObject().apply {
        addProperty("trigger", "minecraft:inventory_changed")
        add("conditions", JsonObject().apply {
            add("items", JsonArray().apply {
                add(JsonObject().apply {
                    add("items", JsonArray().apply { add(item) })
                })
            })
        })
    }

    fun writeRecipe(relative: String, json: JsonObject) {
        val path = dataOutput.resolve("recipes/$relative.json")
        path.parent.createDirectories()
        path.writeText(gson.toJson(json))
    }

    fun shapedResult(item: String, count: Int = 1): JsonObject = JsonObject().apply {
        addProperty("item", item)
        if (count != 1) addProperty("count", count)
    }
    // Housing recipes: one per key type, bg+case_fg texture per type
    run {
        val housingMetalForType: (String) -> String = { type ->
            when (type) {
                "fluid" -> "minecraft:copper_ingot"
                else -> "minecraft:iron_ingot"
            }
        }
        for (type in storageCellGroups.keys) {
            val housingId = "ae2isallyouneed:${type}_cell_housing"
            val metal = housingMetalForType(type)
            val json = JsonObject().apply {
                addProperty("type", "minecraft:crafting_shaped")
                add("pattern", JsonArray().apply {
                    add("aba"); add("b b"); add("ccc")
                })
                add("key", JsonObject().apply {
                    add("a", JsonObject().apply { addProperty("item", "ae2:quartz_glass") })
                    add("b", JsonObject().apply { addProperty("item", "minecraft:redstone") })
                    add("c", JsonObject().apply { addProperty("item", metal) })
                })
                add("result", shapedResult(housingId))
                add("criteria", JsonObject().apply {
                    add("has_redstone", criteriaFor("minecraft:redstone"))
                })
                add("requirements", JsonArray().apply {
                    add(JsonArray().apply { add("has_redstone") })
                })
            }
            writeRecipe("${type}_cell_housing", json)
        }
    }
    // Component recipes (1K .. 256T)
    for ((idx, tier) in tiers.withIndex()) {
        val compId = "ae2isallyouneed:cell_component_${tier.lowercase()}"
        val prevComp = if (idx > 0) "ae2isallyouneed:cell_component_${tiers[idx - 1].lowercase()}" else null
        val json = when (idx) {
            0 -> JsonObject().apply {
                addProperty("type", "minecraft:crafting_shaped")
                add("pattern", JsonArray().apply { add("aba"); add("bcb"); add("aba") })
                add("key", JsonObject().apply {
                    add("a", JsonObject().apply { addProperty("item", "minecraft:redstone") })
                    add("b", JsonObject().apply { addProperty("item", "ae2:certus_quartz_crystal") })
                    add("c", JsonObject().apply { addProperty("item", "ae2:logic_processor") })
                })
                add("result", shapedResult(compId))
                add(
                    "criteria",
                    JsonObject().apply { add("has_logic_processor", criteriaFor("ae2:logic_processor")) })
                add("requirements", JsonArray().apply { add(JsonArray().apply { add("has_logic_processor") }) })
            }

            1 -> JsonObject().apply {
                addProperty("type", "minecraft:crafting_shaped")
                add("pattern", JsonArray().apply { add("aba"); add("cdc"); add("aca") })
                add("key", JsonObject().apply {
                    add("a", JsonObject().apply { addProperty("item", "minecraft:redstone") })
                    add("b", JsonObject().apply { addProperty("item", "ae2:calculation_processor") })
                    add("c", JsonObject().apply { addProperty("item", prevComp!!) })
                    add("d", JsonObject().apply { addProperty("item", "ae2:quartz_glass") })
                })
                add("result", shapedResult(compId))
                add("criteria", JsonObject().apply { add("has_cell_component_1k", criteriaFor(prevComp!!)) })
                add("requirements", JsonArray().apply { add(JsonArray().apply { add("has_cell_component_1k") }) })
            }

            2, 3 -> JsonObject().apply {
                addProperty("type", "minecraft:crafting_shaped")
                add("pattern", JsonArray().apply { add("aba"); add("cdc"); add("aca") })
                add("key", JsonObject().apply {
                    add("a", JsonObject().apply { addProperty("item", "minecraft:glowstone_dust") })
                    add("b", JsonObject().apply { addProperty("item", "ae2:calculation_processor") })
                    add("c", JsonObject().apply { addProperty("item", prevComp!!) })
                    add("d", JsonObject().apply { addProperty("item", "ae2:quartz_glass") })
                })
                add("result", shapedResult(compId))
                add("criteria", JsonObject().apply { add("has_prev", criteriaFor(prevComp!!)) })
                add("requirements", JsonArray().apply { add(JsonArray().apply { add("has_prev") }) })
            }

            else -> JsonObject().apply {
                addProperty("type", "minecraft:crafting_shaped")
                add("pattern", JsonArray().apply { add("aba"); add("cdc"); add("aca") })
                add("key", JsonObject().apply {
                    add("a", JsonObject().apply { addProperty("item", "ae2:sky_dust") })
                    add("b", JsonObject().apply { addProperty("item", "ae2:calculation_processor") })
                    add("c", JsonObject().apply { addProperty("item", prevComp!!) })
                    add("d", JsonObject().apply { addProperty("item", "ae2:quartz_glass") })
                })
                add("result", shapedResult(compId))
                add("criteria", JsonObject().apply { add("has_prev", criteriaFor(prevComp!!)) })
                add("requirements", JsonArray().apply { add(JsonArray().apply { add("has_prev") }) })
            }
        }
        writeRecipe("cell_component_${tier.lowercase()}", json)
    }
    // Cell recipes: shaped (quartz_glass + redstone + component + housing metal) + shapeless (housing + component)
    val cellHousingForType: (String) -> String = { type ->
        "ae2isallyouneed:${type}_cell_housing"
    }
    val cellMetalForType: (String) -> String = { type ->
        when (type) {
            "fluid" -> "minecraft:copper_ingot"
            else -> "minecraft:iron_ingot"
        }
    }
    for ((type, cells) in storageCellGroups) {
        for ((idx, cell) in cells.withIndex()) {
            val tier = tiers[idx]
            val comp = "ae2isallyouneed:cell_component_${tier.lowercase()}"
            val cellId = "ae2isallyouneed:${cell.id}"
            val housing = cellHousingForType(type)
            val metal = cellMetalForType(type)
            // Shaped: same as AE2's item/fluid cell shaped (quartz_glass / redstone / component / metal)
            val shaped = JsonObject().apply {
                addProperty("type", "minecraft:crafting_shaped")
                add("pattern", JsonArray().apply { add("aba"); add("bcb"); add("ddd") })
                add("key", JsonObject().apply {
                    add("a", JsonObject().apply { addProperty("item", "ae2:quartz_glass") })
                    add("b", JsonObject().apply { addProperty("item", "minecraft:redstone") })
                    add("c", JsonObject().apply { addProperty("item", comp) })
                    add("d", JsonObject().apply { addProperty("item", metal) })
                })
                add("result", shapedResult(cellId))
                add("criteria", JsonObject().apply { add("has_component", criteriaFor(comp)) })
                add("requirements", JsonArray().apply { add(JsonArray().apply { add("has_component") }) })
            }
            writeRecipe("${cell.id}", shaped)
            // Shapeless: housing + component
            val shapeless = JsonObject().apply {
                addProperty("type", "minecraft:crafting_shapeless")
                add("ingredients", JsonArray().apply {
                    add(JsonObject().apply { addProperty("item", housing) })
                    add(JsonObject().apply { addProperty("item", comp) })
                })
                add("result", shapedResult(cellId))
                add("criteria", JsonObject().apply { add("has_component", criteriaFor(comp)) })
                add("requirements", JsonArray().apply { add(JsonArray().apply { add("has_component") }) })
            }
            writeRecipe("${cell.id}_storage", shapeless)
        }
    }
    println("[recipes] generated housing + ${tiers.size} components + ${storageCellGroups.values.sumOf { it.size } * 2} cell recipes")
}
