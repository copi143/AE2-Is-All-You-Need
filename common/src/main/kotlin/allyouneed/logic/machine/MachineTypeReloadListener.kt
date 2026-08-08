package allyouneed.logic.machine

import allyouneed.logic.machine.MachineTypeReloadListener.Companion.defaultMachinesTag
import allyouneed.util.logger
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

/**
 * 从 `data/<ns>/machines/types/` 加载数据包机器类别。
 * 同 id 时覆盖代码注册的类型。
 * 机器接受条件 = 类型内物品列表 **或** 物品标签。
 *
 * Loads datapack machine types from `data/<ns>/machines/types/`.
 * Datapack entries override code-registered types with the same id.
 * Machine acceptance = listed items **OR** item tags.
 */
class MachineTypeReloadListener : SimpleJsonResourceReloadListener(GSON, FOLDER) {
    override fun apply(
        map: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller,
    ) {
        // 先失效旧数据包实例并恢复代码类型，再构造新实例（构造时自动 install）
        MachineType.beginDatapackReload()
        var loaded = 0
        for ((fileId, element) in map) {
            try {
                if (!element.isJsonObject) {
                    logger.warn("Skipping non-object machine type {}", fileId)
                    continue
                }
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
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        fun parseType(fileId: ResourceLocation, json: JsonObject): MachineType {
            val id = json.get("id")?.asString ?: "${fileId.namespace}:${fileId.path.substringAfterLast('/')}"
            val nameEl = json.get("name")
            val displayName = when {
                nameEl == null -> Component.literal(id)
                nameEl.isJsonPrimitive -> {
                    val s = nameEl.asString
                    if (s.startsWith("gui.") || s.contains(".")) Component.translatable(s)
                    else Component.literal(s)
                }

                else -> Component.Serializer.fromJson(nameEl) ?: Component.literal(id)
            }

            val icon = when {
                json.has("icon") && json.get("icon").isJsonObject -> ManualMachineRecipeReloadListener.parseItemStack(
                    json.getAsJsonObject("icon")
                )

                json.has("icon") -> {
                    val item = BuiltInRegistries.ITEM.get(ResourceLocation(json.get("icon").asString))
                    ItemStack(item)
                }

                else -> ItemStack(Items.CRAFTING_TABLE)
            }

            val inputSlots = json.get("input_slots")?.asInt ?: 1
            // 默认：id "ns:foo/bar" → 物品标签 "ns:machines/foo/bar"（与显式列表 OR）
            val autoTag = json.get("auto_tag")?.asBoolean ?: true
            val matcher = parseMatcher(json, id, autoTag)
            val sourceId = json.get("recipe_source")?.asString
            val recipeSource = VanillaRecipeSources.fromId(sourceId)
            val recipeType = sourceId?.let { VanillaRecipeSources.recipeTypeFromId(it) }

            return MachineType(
                id = id,
                name = displayName,
                icon = if (icon.isEmpty) ItemStack(Items.CRAFTING_TABLE) else icon,
                inputSlots = inputSlots.coerceIn(1, 9),
                machineMatcher = matcher,
                recipeSource = recipeSource,
                recipeType = recipeType,
                fromDatapack = true,
            )
        }

        /**
         * 由类型 id 推导默认物品标签：
         * `ae2isallyouneed:example_custom` → `ae2isallyouneed:machines/example_custom`
         *
         * Default item tag derived from type id.
         */
        fun defaultMachinesTag(typeId: String): ResourceLocation? {
            val rl = ResourceLocation.tryParse(typeId) ?: return null
            return ResourceLocation(rl.namespace, "machines/${rl.path}")
        }

        /**
         * 收集多种写法中的物品 id 与标签，全部 OR。
         * [autoTag] 为 true 时额外加入 [defaultMachinesTag]。
         *
         * Collects item ids + tags from several shapes; all OR'd.
         * When [autoTag] is true, also includes [defaultMachinesTag] for [typeId].
         *
         * ```json
         * "machines": ["minecraft:furnace"],
         * "tags": []
         * ```
         * 仍接受标签 `ns:machines/<type path>` 中的物品。
         */
        private fun parseMatcher(json: JsonObject, typeId: String, autoTag: Boolean): MachineItemMatcher {
            val items = LinkedHashSet<Item>()
            val tags = LinkedHashSet<ResourceLocation>()

            fun addItems(el: JsonElement?) {
                if (el == null) return
                items += parseItemList(el)
            }

            fun addTags(el: JsonElement?) {
                if (el == null) return
                for (tagId in parseStringList(el)) {
                    ResourceLocation.tryParse(tagId)?.let { tags += it }
                }
            }

            fun addTagString(s: String?) {
                if (s.isNullOrBlank()) return
                ResourceLocation.tryParse(s)?.let { tags += it }
            }

            val machines = json.get("machines")
            when {
                machines != null && machines.isJsonArray -> addItems(machines)
                machines != null && machines.isJsonObject -> {
                    val obj = machines.asJsonObject
                    addItems(obj.get("items") ?: obj.get("machines"))
                    addTags(obj.get("tags"))
                    addTagString(obj.get("tag")?.asString)
                }

                machines != null && machines.isJsonPrimitive -> addItems(
                    com.google.gson.JsonArray().apply { add(machines.asString) },
                )
            }

            // 顶层字段与 machines{} 并存
            addItems(json.get("items"))
            addTags(json.get("tags"))
            addTagString(json.get("tag")?.asString)
            addTagString(json.get("machines_tag")?.asString)
            addTags(json.get("machines_tags"))

            if (autoTag) {
                defaultMachinesTag(typeId)?.let { tags += it }
            }

            val matchers = ArrayList<MachineItemMatcher>()
            if (items.isNotEmpty()) matchers += DefaultItemsMatcher(items)
            for (tag in tags) {
                matchers += ItemTagMatcher(TagKey.create(Registries.ITEM, tag))
            }
            if (matchers.isEmpty()) {
                logger.warn("Machine type has no machine matchers; nothing will accept it")
            }
            return when (matchers.size) {
                0 -> MachineItemMatcher { false }
                1 -> matchers[0]
                else -> AnyMatcher(matchers)
            }
        }

        private fun parseStringList(el: JsonElement): List<String> {
            if (!el.isJsonArray) return emptyList()
            return el.asJsonArray.mapNotNull {
                when {
                    it.isJsonPrimitive -> it.asString
                    it.isJsonObject && it.asJsonObject.has("id") -> it.asJsonObject.get("id").asString
                    else -> null
                }
            }
        }

        private fun parseItemList(el: JsonElement): List<Item> {
            val out = ArrayList<Item>()
            for (s in parseStringList(el)) {
                val rl = ResourceLocation.tryParse(s) ?: continue
                BuiltInRegistries.ITEM.getOptional(rl).ifPresent { out += it }
            }
            return out
        }
    }
}
