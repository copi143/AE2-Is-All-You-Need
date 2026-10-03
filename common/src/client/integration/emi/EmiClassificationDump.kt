package allyouneed.client.integration.emi

import allyouneed.Platform
import allyouneed.client.integration.emi.fold.EmiFoldCluster
import allyouneed.util.logger
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer
import dev.emi.emi.runtime.EmiReloadManager
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TieredItem
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.material.Fluid
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** 仅由客户端命令调用；主线程采集游戏对象，后台线程只写已脱离游戏状态的 JSON。 */
object EmiClassificationDump {
    private val running = AtomicBoolean()
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun request(): Int {
        val client = Minecraft.getInstance()
        if (client.level == null || client.player == null || !EmiReloadManager.isLoaded()) {
            message("请进入世界并等待 EMI 加载完成后再导出。")
            return 0
        }
        if (!running.compareAndSet(false, true)) {
            message("分类信息正在导出，请等待完成。")
            return 0
        }
        message("开始采集分类信息，大型整合包可能短暂停顿。")
        try {
            val snapshot = Snapshot(client).collect()
            val directory = client.gameDirectory.toPath().resolve(".tmp/emi-classification")
            CompletableFuture.runAsync {
                // 每次创建独立文件，保留不同整合包/语言/资源重载前后的样本。
                Files.createDirectories(directory)
                val path = Files.createTempFile(directory, "items-", ".json")
                try {
                    Files.newBufferedWriter(path).use { gson.toJson(snapshot, it) }
                } catch (e: Exception) {
                    Files.deleteIfExists(path)
                    throw e
                }
                logger.info("EMI classification dump: {}", path.toAbsolutePath())
                client.execute {
                    message("分类信息已导出：${path.toAbsolutePath()}（字段错误 ${snapshot["errorCount"]}）")
                }
            }.whenComplete { _, error ->
                running.set(false)
                if (error != null) {
                    logger.error("Failed to write EMI classification dump", error)
                    client.execute { message("分类信息写入失败，详见客户端日志。") }
                }
            }
        } catch (e: Exception) {
            running.set(false)
            logger.error("Failed to collect EMI classification dump", e)
            message("分类信息采集失败：${e.message}，详见客户端日志。")
            return 0
        }
        return 1
    }

    private fun message(text: String) {
        Minecraft.getInstance().gui.chat.addMessage(Component.literal("[EMI 分类导出] $text"))
    }

    private class Snapshot(private val client: Minecraft) {
        private var errorCount = 0
        private val classes = JsonObject()

        /** 字段失败保留明确错误，其余字段继续采集，避免把读取失败误判为没有特征。 */
        private fun JsonObject.field(name: String, read: () -> Any?) {
            try {
                val value = read()
                add(name, if (value is JsonElement) value else gson.toJsonTree(value))
            } catch (e: Exception) {
                errorCount++
                val errors = getAsJsonObject("errors") ?: JsonObject().also { add("errors", it) }
                errors.addProperty(name, "${e.javaClass.name}: ${e.message}")
            }
        }

        private fun typeOf(value: Any): String {
            val type = value.javaClass
            if (!classes.has(type.name)) {
                val hierarchy = generateSequence<Class<*>>(type) { it.superclass }.toList()
                val interfaces = sortedSetOf<String>()
                fun visit(current: Class<*>) {
                    for (iface in current.interfaces) {
                        if (interfaces.add(iface.name)) visit(iface)
                    }
                }
                hierarchy.forEach(::visit)
                classes.add(type.name, gson.toJsonTree(mapOf(
                    "hierarchy" to hierarchy.map { it.name },
                    "interfaces" to interfaces,
                )))
            }
            return type.name
        }

        fun collect(): JsonObject {
            check(client.isSameThread) { "分类信息必须在客户端主线程采集" }
            val source = EmiApi.getIndexStacks()
            val index = source.toList()
            val manager = EmiApi.getRecipeManager()
            val root = JsonObject()
            root.addProperty("schemaVersion", 1)
            root.addProperty("capturedAt", Instant.now().toString())
            root.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name)
            root.addProperty("platform", Platform.name)
            root.addProperty("environment", Platform.envName)
            root.addProperty("language", client.options.languageCode)
            root.addProperty("advancedTooltips", client.options.advancedItemTooltips)
            root.addProperty("operatorTabPermission", client.player!!.canUseGameMasterBlocks())
            root.addProperty("recipeCount", manager.recipes.size)

            root.field("creativeTabs") {
                CreativeModeTabs.tryRebuildTabContents(
                    client.level!!.enabledFeatures(), client.player!!.canUseGameMasterBlocks(),
                    client.level!!.registryAccess(),
                )
                JsonArray().apply {
                    for (tab in BuiltInRegistries.CREATIVE_MODE_TAB) {
                        if (tab.type != CreativeModeTab.Type.CATEGORY) continue
                        add(JsonObject().apply {
                            field("id") { BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString() }
                            field("name") { tab.displayName.string }
                            field("members") {
                                tab.displayItems.map { stack -> identity(stack) }
                            }
                        })
                    }
                }
            }

            // 注册表包含 EMI 隐藏/未收录的物品；EMI 索引单独保留顺序与 NBT 变体。
            root.add("items", JsonArray().apply {
                for (item in BuiltInRegistries.ITEM) {
                    add(JsonObject().apply {
                        addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString())
                        field("class") { typeOf(item) }
                        field("descriptionId") { item.descriptionId }
                        field("defaultStack") { itemDetails(item.defaultInstance) }
                        if (item is BlockItem) field("block") {
                            val block = item.block
                            JsonObject().apply {
                                field("id") { BuiltInRegistries.BLOCK.getKey(block).toString() }
                                field("class") { typeOf(block) }
                                field("descriptionId") { block.descriptionId }
                                field("tags") { block.builtInRegistryHolder().tags().map { it.location().toString() }.sorted().toList() }
                                field("properties") {
                                    block.stateDefinition.properties.associate { property ->
                                        property.name to property.possibleValues.map { it.toString() }.sorted()
                                    }
                                }
                                field("defaultState") { block.defaultBlockState().toString() }
                                field("hasBlockEntity") { block.defaultBlockState().hasBlockEntity() }
                            }
                        }
                    })
                }
            })

            root.add("emiStacks", JsonArray().apply {
                index.forEachIndexed { position, stack ->
                    add(JsonObject().apply {
                        addProperty("index", position)
                        field("id") { stack.id.toString() }
                        field("emiClass") { typeOf(stack) }
                        field("keyClass") { typeOf(stack.key) }
                        field("name") { stack.name.string }
                        field("nbt") { stack.nbt?.toString() }
                        field("serialized") { EmiIngredientSerializer.getSerialized(stack) }
                        field("tooltip") { stack.tooltipText.map { it.string } }
                        field("item") { stack.itemStack.takeUnless { it.isEmpty }?.let(::itemDetails) }
                        field("fluidTags") {
                            (stack.key as? Fluid)?.builtInRegistryHolder()?.tags()
                                ?.map { it.location().toString() }?.sorted()?.toList()
                        }
                        field("legacyCandidateKeys") {
                            EmiFoldCluster.compositeKeys(stack.id.namespace, stack.id.path, stack.key.javaClass.name)
                        }
                        field("inputRecipeCategories") {
                            manager.getRecipesByInput(stack).groupingBy { it.category.id.toString() }.eachCount().toSortedMap()
                        }
                        field("outputRecipeCategories") {
                            manager.getRecipesByOutput(stack).groupingBy { it.category.id.toString() }.eachCount().toSortedMap()
                        }
                    })
                }
            })
            root.field("recipeCategories") {
                manager.categories.map { category ->
                    JsonObject().apply {
                        field("id") { category.id.toString() }
                        field("name") { category.name.string }
                        field("workstations") {
                            manager.getWorkstations(category).flatMap { it.emiStacks }
                                .map { EmiIngredientSerializer.getSerialized(it) }
                        }
                    }
                }
            }
            // EMI 在独立线程重载。发现快照期间更换索引/管理器则要求重试，避免输出混合样本。
            check(EmiReloadManager.isLoaded() && EmiApi.getIndexStacks() === source && EmiApi.getRecipeManager() === manager) {
                "采集期间 EMI 发生重载，请等待加载完成后重试"
            }
            root.add("classes", classes)
            root.addProperty("itemCount", root.getAsJsonArray("items").size())
            root.addProperty("emiStackCount", index.size)
            root.addProperty("errorCount", errorCount)
            return root
        }

        private fun identity(stack: ItemStack): JsonObject = JsonObject().apply {
            addProperty("id", BuiltInRegistries.ITEM.getKey(stack.item).toString())
            addProperty("nbt", stack.tag?.toString())
        }

        private fun itemDetails(stack: ItemStack): JsonObject = identity(stack).apply {
            field("name") { stack.hoverName.string }
            field("descriptionId") { stack.descriptionId }
            field("tags") { stack.tags.map { it.location().toString() }.sorted().toList() }
            field("maxStackSize") { stack.maxStackSize }
            field("maxDamage") { stack.maxDamage }
            field("damage") { stack.damageValue }
            field("damageable") { stack.isDamageableItem }
            field("enchantable") { stack.isEnchantable }
            field("enchantmentValue") { stack.item.enchantmentValue }
            field("edible") { stack.isEdible }
            field("rarity") { stack.rarity.name }
            field("useAnimation") { stack.useAnimation.name }
            field("useDuration") { stack.useDuration }
            field("equipmentAttributes") {
                EquipmentSlot.values().associate { slot ->
                    slot.name to stack.getAttributeModifiers(slot).entries().map { (attribute, modifier) ->
                        mapOf(
                            "attribute" to BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString(),
                            "amount" to modifier.amount,
                            "operation" to modifier.operation.name,
                        )
                    }
                }
            }
            (stack.item as? TieredItem)?.let { tool ->
                field("toolTier") {
                    mapOf(
                        "class" to typeOf(tool.tier), "level" to tool.tier.level,
                        "uses" to tool.tier.uses, "speed" to tool.tier.speed,
                        "attackDamageBonus" to tool.tier.attackDamageBonus,
                        "enchantmentValue" to tool.tier.enchantmentValue,
                    )
                }
            }
            (stack.item as? ArmorItem)?.let { armor ->
                field("armor") {
                    mapOf(
                        "slot" to armor.equipmentSlot.name,
                        "materialClass" to typeOf(armor.material),
                        "materialName" to armor.material.name,
                        "defense" to armor.defense, "toughness" to armor.toughness,
                    )
                }
            }
            field("craftingRemainder") { stack.item.craftingRemainingItem?.let { BuiltInRegistries.ITEM.getKey(it).toString() } }
            field("tooltip") { stack.getTooltipLines(client.player, TooltipFlag.Default.NORMAL).map { it.string } }
            field("advancedTooltip") { stack.getTooltipLines(client.player, TooltipFlag.Default.ADVANCED).map { it.string } }
        }
    }
}
