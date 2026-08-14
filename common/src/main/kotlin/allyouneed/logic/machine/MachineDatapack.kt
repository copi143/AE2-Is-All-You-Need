package allyouneed.logic.machine

import allyouneed.util.bigint.BigIngredient
import allyouneed.util.bigint.BigStack
import allyouneed.util.logger
import allyouneed.util.rl
import appeng.api.stacks.AEItemKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.tags.TagKey
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import java.math.BigInteger

private val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

// ---------------------------------------------------------------------------
// 共用 JSON 工具
// ---------------------------------------------------------------------------

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

private fun parseStringList(el: JsonElement?): List<String> {
    if (el == null || !el.isJsonArray) return emptyList()
    return el.asJsonArray.mapNotNull {
        when {
            it.isJsonPrimitive -> it.asString
            it.isJsonObject && it.asJsonObject.has("id") -> it.asJsonObject.get("id").asString
            else -> null
        }
    }
}

internal fun parseItemStack(json: JsonObject, count: BigInteger = parseAmount(json)): ItemStack {
    val itemId = json.get("item")?.asString ?: throw IllegalArgumentException("missing item")
    val item = BuiltInRegistries.ITEM.getOptional(ResourceLocation(itemId)).orElseThrow {
        IllegalArgumentException("unknown item $itemId")
    }
    val c = count.min(BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt().coerceAtLeast(1)
    return ItemStack(item, c)
}

internal fun parseBigStack(json: JsonObject): BigStack {
    val amount = parseAmount(json)
    val key = AEItemKey.of(parseItemStack(json, amount))
        ?: throw IllegalArgumentException("invalid item for BigStack")
    return BigStack.from(key, amount)
}

private fun parseBigIngredient(el: JsonElement?): BigIngredient {
    if (el == null || el.isJsonNull) return BigIngredient.ofEmpty()
    if (!el.isJsonObject) throw IllegalArgumentException("input must be object")
    val obj = el.asJsonObject
    if (obj.size() == 0) return BigIngredient.ofEmpty()
    val count = parseAmount(obj)
    // 仅 item → 精确
    if (obj.has("item") && !obj.has("tag") && !obj.has("items")) {
        val key = AEItemKey.of(parseItemStack(obj, count))
            ?: throw IllegalArgumentException("invalid item in input")
        return BigIngredient.from(key, count)
    }
    return BigIngredient.from(Ingredient.fromJson(obj), count)
}

// ---------------------------------------------------------------------------
// types
// ---------------------------------------------------------------------------

/**
 * `data/<ns>/machines/types/`
 * 字段：id, name, icon, input_slots, machines[], tags[], auto_tag, recipe_source
 */
class MachineTypeReloadListener : SimpleJsonResourceReloadListener(GSON, FOLDER) {
    override fun apply(
        map: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller,
    ) {
        MachineType.beginDatapackReload()
        var loaded = 0
        for ((fileId, element) in map) {
            try {
                if (!element.isJsonObject) continue
                parseType(fileId, element.asJsonObject)
                loaded++
            } catch (t: Throwable) {
                logger.error("Failed to parse machine type {}", fileId, t)
            }
        }
        MachineType.endDatapackReload(loaded)
    }

    companion object {
        const val FOLDER = "machines/types"

        fun parseType(fileId: ResourceLocation, json: JsonObject): MachineType {
            val id = json.get("id")?.asString
                ?: "${fileId.namespace}:${fileId.path.substringAfterLast('/')}"

            val nameEl = json.get("name")
            val name = when {
                nameEl == null -> Component.literal(id)
                nameEl.isJsonPrimitive -> {
                    val s = nameEl.asString
                    if (s.startsWith("gui.") || s.contains('.')) Component.translatable(s)
                    else Component.literal(s)
                }
                else -> Component.Serializer.fromJson(nameEl) ?: Component.literal(id)
            }

            val icon = when {
                json.has("icon") && json.get("icon").isJsonObject ->
                    parseItemStack(json.getAsJsonObject("icon"))
                json.has("icon") -> ItemStack(
                    BuiltInRegistries.ITEM.get(ResourceLocation(json.get("icon").asString)),
                )
                else -> ItemStack(Items.CRAFTING_TABLE)
            }.let { if (it.isEmpty) ItemStack(Items.CRAFTING_TABLE) else it }

            val autoTag = json.get("auto_tag")?.asBoolean ?: true
            val matcher = parseMatcher(json, id, autoTag)
            val sourceId = json.get("recipe_source")?.asString

            return MachineType(
                id = id,
                name = name,
                icon = icon,
                inputSlots = (json.get("input_slots")?.asInt ?: 1).coerceIn(1, 9),
                machineMatcher = matcher,
                recipeSource = MachineRecipes.source(sourceId),
                recipeType = MachineRecipes.recipeType(sourceId),
                fromDatapack = true,
            )
        }

        private fun parseMatcher(json: JsonObject, typeId: String, autoTag: Boolean): MachineItemMatcher {
            val items = LinkedHashSet<Item>()
            for (s in parseStringList(json.get("machines"))) {
                ResourceLocation.tryParse(s)?.let { rl ->
                    BuiltInRegistries.ITEM.getOptional(rl).ifPresent { items += it }
                }
            }
            val tags = LinkedHashSet<TagKey<Item>>()
            for (s in parseStringList(json.get("tags"))) {
                ResourceLocation.tryParse(s)?.let { tags += TagKey.create(Registries.ITEM, it) }
            }
            if (autoTag) {
                ResourceLocation.tryParse(typeId)?.let { rl ->
                    tags += TagKey.create(Registries.ITEM, ResourceLocation(rl.namespace, "machines/${rl.path}"))
                }
            }
            return MachineItemMatcher(items, tags)
        }
    }
}

// ---------------------------------------------------------------------------
// recipes
// ---------------------------------------------------------------------------

/**
 * `data/<ns>/machines/recipes/`
 * 字段：machine_type, inputs[], outputs[]（兼容 output）, remainders?
 */
class ManualMachineRecipeReloadListener :
    SimpleJsonResourceReloadListener(GSON, FOLDER) {

    override fun apply(
        map: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller,
    ) {
        val recipes = ArrayList<ManualMachineRecipe>()
        for ((id, element) in map) {
            try {
                if (!element.isJsonObject) continue
                recipes += parseRecipe(id, element.asJsonObject)
            } catch (t: Throwable) {
                logger.error("Failed to parse machine recipe {}", id, t)
            }
        }
        ManualMachineRecipes.replaceAll(recipes)
    }

    companion object {
        const val FOLDER = "machines/recipes"

        fun parseRecipe(id: ResourceLocation, json: JsonObject): ManualMachineRecipe {
            val typeId = json.get("machine_type")?.asString
                ?: throw IllegalArgumentException("missing machine_type")
            val machineType = MachineType.byId(typeId)
                ?: throw IllegalArgumentException("unknown machine_type: $typeId")

            val inputsArr = json.getAsJsonArray("inputs")
                ?: throw IllegalArgumentException("missing inputs")
            val inputs = inputsArr.map { parseBigIngredient(it) }

            val outputs = parseOutputs(json)
            require(outputs.isNotEmpty()) { "empty outputs" }

            val remainders = json.get("remainders")?.let { el ->
                val arr = el.asJsonArray
                List(maxOf(arr.size(), inputs.size)) { i ->
                    if (i >= arr.size()) return@List null
                    val e = arr[i]
                    when {
                        e == null || e.isJsonNull -> null
                        e.isJsonObject && e.asJsonObject.size() == 0 -> null
                        e.isJsonObject -> parseBigStack(e.asJsonObject)
                        else -> null
                    }
                }
            }

            return ManualMachineRecipe(id, machineType, inputs, outputs, remainders)
        }

        private fun parseOutputs(json: JsonObject): List<BigStack> {
            val el = json.get("outputs") ?: json.get("output")
                ?: throw IllegalArgumentException("missing outputs")
            return if (el.isJsonArray) {
                el.asJsonArray.mapNotNull {
                    if (it == null || it.isJsonNull) null else parseBigStack(it.asJsonObject)
                }
            } else {
                listOf(parseBigStack(el.asJsonObject))
            }
        }
    }
}
