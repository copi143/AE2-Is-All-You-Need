package allyouneed.resgen

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@DslMarker
annotation class RecipeDsl

@RecipeDsl
abstract class CraftingRecipe internal constructor(private val modId: String) {
    private val criteria = linkedMapOf<String, String>()

    protected fun itemId(item: String): String = if (':' in item) item else "$modId:$item"

    protected fun ingredientJson(item: String) = JsonObject().apply {
        addProperty("item", itemId(item))
    }

    /** Preserves the existing recipe criteria/requirements fields; does not emit an advancement. */
    fun unlock(name: String, item: String) {
        require(name.isNotBlank()) { "Unlock name must not be blank" }
        require(name !in criteria) { "Duplicate unlock criterion: $name" }
        criteria[name] = itemId(item)
    }

    protected abstract fun ingredientsJson(): JsonObject

    internal fun build(result: String, count: Int): JsonObject {
        require(count > 0) { "Result count must be positive" }
        return ingredientsJson().apply {
            add("result", JsonObject().apply {
                addProperty("item", itemId(result))
                if (count != 1) addProperty("count", count)
            })
            if (criteria.isNotEmpty()) {
                add("criteria", JsonObject().apply {
                    for ((name, item) in criteria) {
                        add(name, JsonObject().apply {
                            addProperty("trigger", "minecraft:inventory_changed")
                            add("conditions", JsonObject().apply {
                                add("items", JsonArray().apply {
                                    add(JsonObject().apply {
                                        add("items", JsonArray().apply { add(item) })
                                    })
                                })
                            })
                        })
                    }
                })
                // Each declared criterion is required, matching the existing requirements format.
                add("requirements", JsonArray().apply {
                    for (name in criteria.keys) add(JsonArray().apply { add(name) })
                })
            }
        }
    }
}

class ShapedRecipe internal constructor(modId: String) : CraftingRecipe(modId) {
    private var rows = emptyList<String>()
    private val keys = linkedMapOf<Char, String>()

    fun pattern(vararg rows: String) {
        require(this.rows.isEmpty()) { "Pattern is already defined" }
        require(rows.size in 1..3 && rows.all { it.length in 1..3 && it.length == rows[0].length }) {
            "Pattern must be a rectangle between 1x1 and 3x3"
        }
        this.rows = rows.toList()
    }

    fun key(symbol: Char, item: String) {
        require(symbol != ' ') { "Space is reserved for empty slots" }
        require(symbol !in keys) { "Duplicate pattern key: $symbol" }
        keys[symbol] = item
    }

    override fun ingredientsJson(): JsonObject {
        require(rows.isNotEmpty()) { "A shaped recipe needs a pattern" }
        val symbols = rows.flatMap { it.toList() }.filter { it != ' ' }.toSet()
        require(symbols.isNotEmpty()) { "Pattern must contain an ingredient" }
        require(symbols == keys.keys) {
            "Pattern keys do not match: undefined=${symbols - keys.keys}, unused=${keys.keys - symbols}"
        }
        return JsonObject().apply {
            addProperty("type", "minecraft:crafting_shaped")
            add("pattern", JsonArray().apply { rows.forEach { add(it) } })
            add("key", JsonObject().apply {
                for ((symbol, item) in keys) add(symbol.toString(), ingredientJson(item))
            })
        }
    }
}

class ShapelessRecipe internal constructor(modId: String) : CraftingRecipe(modId) {
    private val ingredients = mutableListOf<String>()

    fun ingredient(item: String) {
        ingredients += item
    }

    override fun ingredientsJson(): JsonObject {
        require(ingredients.size in 1..9) { "A shapeless recipe needs 1 to 9 ingredients" }
        return JsonObject().apply {
            addProperty("type", "minecraft:crafting_shapeless")
            add("ingredients", JsonArray().apply {
                for (item in ingredients) add(ingredientJson(item))
            })
        }
    }
}

@RecipeDsl
class RecipeGen(private val modId: String, private val output: Path) {
    private val recipes = linkedMapOf<String, JsonObject>()

    /** Unqualified item IDs use [modId]; [name] is the recipe path under `recipes/`. */
    fun shaped(name: String, result: String = name, count: Int = 1, init: ShapedRecipe.() -> Unit) {
        register(name, ShapedRecipe(modId).apply(init).build(result, count))
    }

    fun shapeless(name: String, result: String = name, count: Int = 1, init: ShapelessRecipe.() -> Unit) {
        register(name, ShapelessRecipe(modId).apply(init).build(result, count))
    }

    private fun register(name: String, json: JsonObject) {
        require(name !in recipes) { "Duplicate recipe: $name" }
        recipes[name] = json
    }

    fun generate() {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        for ((name, json) in recipes) {
            val path = output.resolve("recipes/$name.json")
            path.parent.createDirectories()
            path.writeText(gson.toJson(json))
        }
    }
}

fun recipeGen(modId: String, output: Path, init: RecipeGen.() -> Unit) {
    RecipeGen(modId, output).apply(init).generate()
}
