package allyouneed.logic.machine

import allyouneed.util.bigint.BigIngredient
import allyouneed.util.bigint.BigStack
import allyouneed.util.logger
import appeng.api.stacks.AEItemKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import java.math.BigInteger

/**
 * 数据包手动配方，目录 `data/<ns>/machines/recipes/`。
 * 按 [MachineType.id] 字符串索引，避免重载后实例失效。
 *
 * Datapack manual recipes under `data/<ns>/machines/recipes/`.
 * Indexed by [MachineType.id] string so reload-safe.
 */
object ManualMachineRecipes {
    const val FOLDER = "machines/recipes"

    @Volatile
    private var byTypeId: Map<String, List<ManualMachineRecipe>> = emptyMap()

    @JvmStatic
    fun all(): Map<String, List<ManualMachineRecipe>> = byTypeId

    @JvmStatic
    fun forType(machineType: MachineType): List<ManualMachineRecipe> {
        if (!machineType.valid) return emptyList()
        return byTypeId[machineType.id] ?: emptyList()
    }

    @JvmStatic
    fun forTypeId(machineTypeId: String): List<ManualMachineRecipe> =
        byTypeId[machineTypeId] ?: emptyList()

    @JvmStatic
    fun match(machineType: MachineType, container: Container): ManualMachineRecipe? {
        for (recipe in forType(machineType)) {
            if (recipe.matches(container)) return recipe
        }
        return null
    }

    @JvmStatic
    fun resolve(machineType: MachineType, container: Container): ItemStack? =
        match(machineType, container)?.primaryOutputStack()

    @JvmStatic
    fun remainders(machineType: MachineType, container: Container): List<ItemStack>? =
        match(machineType, container)?.remainderStacks(container)

    @JvmStatic
    fun replaceAll(recipes: Collection<ManualMachineRecipe>) {
        val map = LinkedHashMap<String, MutableList<ManualMachineRecipe>>()
        for (recipe in recipes) {
            map.getOrPut(recipe.machineType.id) { ArrayList() }.add(recipe)
        }
        byTypeId = map.mapValues { it.value.toList() }
        logger.info("Loaded {} manual machine recipe(s) across {} type(s)", recipes.size, byTypeId.size)
    }

    fun clear() {
        byTypeId = emptyMap()
    }
}

/**
 * 手动配方 JSON 重载监听器。
 * Reload listener for manual machine recipe JSON.
 */
class ManualMachineRecipeReloadListener : SimpleJsonResourceReloadListener(GSON, ManualMachineRecipes.FOLDER) {

    override fun apply(
        map: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller,
    ) {
        val recipes = ArrayList<ManualMachineRecipe>()
        for ((id, element) in map) {
            try {
                if (!element.isJsonObject) {
                    logger.warn("Skipping non-object machine recipe {}", id)
                    continue
                }
                recipes += parseRecipe(id, element.asJsonObject)
            } catch (t: Throwable) {
                logger.error("Failed to parse machine recipe {}", id, t)
            }
        }
        ManualMachineRecipes.replaceAll(recipes)
    }

    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        fun parseRecipe(id: ResourceLocation, json: JsonObject): ManualMachineRecipe {
            val typeId = json.get("machine_type")?.asString
                ?: throw IllegalArgumentException("missing machine_type")
            val machineType = MachineType.byId(typeId)
                ?: throw IllegalArgumentException("unknown or invalid machine_type: $typeId")

            val inputs = parseInputs(json)
            val outputs = parseOutputs(json)
            if (outputs.isEmpty()) throw IllegalArgumentException("empty outputs")

            val remainders = when {
                json.has("remainders") -> parseOptionalBigStackList(json.get("remainders"), inputs.size)
                else -> null
            }

            return ManualMachineRecipe(
                id = id,
                machineType = machineType,
                inputs = inputs,
                outputs = outputs,
                remainders = remainders,
            )
        }

        /**
         * `inputs` 每项：
         * - `{ "item": "…", "count": n }` → 精确 [BigIngredient]（同 BigStack 键）
         * - `{ "tag": "…", "count": n }` / Ingredient JSON → 通配 [BigIngredient]
         * - `{}` / null → 空槽
         * 或 `pattern` + `key` 有形状简写。
         */
        private fun parseInputs(json: JsonObject): List<BigIngredient> {
            if (json.has("pattern") && json.has("key")) {
                return parseShaped(json.getAsJsonArray("pattern"), json.getAsJsonObject("key"))
            }
            val arr = json.getAsJsonArray("inputs")
                ?: throw IllegalArgumentException("missing inputs or pattern/key")
            return arr.map { el -> parseInputElement(el) }
        }

        private fun parseInputElement(el: JsonElement?): BigIngredient {
            if (el == null || el.isJsonNull) return BigIngredient.ofEmpty()
            if (!el.isJsonObject) throw IllegalArgumentException("input must be object")
            val obj = el.asJsonObject
            if (obj.size() == 0) return BigIngredient.ofEmpty()

            val count = parseAmount(obj)

            // 精确 item → BigIngredient.from(AEKey, amount)
            if (obj.has("item") && !obj.has("tag") && !obj.has("items")) {
                val itemStack = parseItemStack(obj, count)
                val key = AEItemKey.of(itemStack)
                    ?: throw IllegalArgumentException("invalid item in input")
                return BigIngredient.from(key, count)
            }

            // 通配：Ingredient + amount
            val ingredient = Ingredient.fromJson(obj)
            return BigIngredient.from(ingredient, count)
        }

        private fun parseShaped(pattern: JsonArray, key: JsonObject): List<BigIngredient> {
            val rows = pattern.map { it.asString }
            require(rows.isNotEmpty()) { "empty pattern" }
            val width = rows.maxOf { it.length }.coerceAtMost(3)
            val height = rows.size.coerceAtMost(3)
            val ingredients = MutableList(9) { BigIngredient.ofEmpty() }
            val keyMap = HashMap<Char, BigIngredient>()
            for ((k, v) in key.entrySet()) {
                require(k.length == 1) { "key must be single char: $k" }
                keyMap[k[0]] = parseInputElement(v)
            }
            for (y in 0 until height) {
                val row = rows[y]
                for (x in 0 until width) {
                    val ch = if (x < row.length) row[x] else ' '
                    if (ch == ' ') continue
                    val ing = keyMap[ch] ?: throw IllegalArgumentException("unknown pattern key '$ch'")
                    ingredients[y * 3 + x] = ing
                }
            }
            return ingredients
        }

        private fun parseOutputs(json: JsonObject): List<BigStack> {
            when {
                json.has("outputs") -> {
                    val arr = json.getAsJsonArray("outputs")
                    return arr.mapNotNull { el ->
                        if (el == null || el.isJsonNull) return@mapNotNull null
                        parseBigStack(el.asJsonObject)
                    }
                }
                json.has("output") -> {
                    val el = json.get("output")
                    if (el.isJsonArray) {
                        return el.asJsonArray.mapNotNull {
                            if (it == null || it.isJsonNull) null else parseBigStack(it.asJsonObject)
                        }
                    }
                    return listOf(parseBigStack(el.asJsonObject))
                }
                else -> throw IllegalArgumentException("missing output/outputs")
            }
        }

        private fun parseOptionalBigStackList(el: JsonElement, minSize: Int): List<BigStack?> {
            val arr = el.asJsonArray
            val list = ArrayList<BigStack?>()
            for (i in 0 until arr.size()) {
                val e = arr[i]
                list += when {
                    e == null || e.isJsonNull -> null
                    e.isJsonObject && e.asJsonObject.size() == 0 -> null
                    e.isJsonObject -> parseBigStack(e.asJsonObject)
                    else -> null
                }
            }
            while (list.size < minSize) list += null
            return list
        }

        fun parseBigStack(json: JsonObject): BigStack {
            val amount = parseAmount(json)
            val itemStack = parseItemStack(json, amount)
            val key = AEItemKey.of(itemStack)
                ?: throw IllegalArgumentException("invalid item for BigStack")
            return BigStack.from(key, amount)
        }

        fun parseItemStack(json: JsonObject, count: BigInteger = parseAmount(json)): ItemStack {
            val itemId = json.get("item")?.asString
                ?: throw IllegalArgumentException("stack missing item")
            val rl = ResourceLocation(itemId)
            val item = BuiltInRegistries.ITEM.getOptional(rl).orElseThrow {
                IllegalArgumentException("unknown item $rl")
            }
            val c = count.min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt().coerceAtLeast(1)
            return ItemStack(item, c)
        }

        private fun parseAmount(json: JsonObject): BigInteger {
            for (field in listOf("count", "amount")) {
                if (!json.has(field)) continue
                val el = json.get(field)
                if (el.isJsonPrimitive) {
                    if (el.asJsonPrimitive.isNumber) return BigInteger.valueOf(el.asLong)
                    if (el.asJsonPrimitive.isString) return BigInteger(el.asString)
                }
            }
            return BigInteger.ONE
        }
    }
}
