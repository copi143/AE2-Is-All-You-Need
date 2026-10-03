package allyouneed.client.integration.emi.fold

import allyouneed.client.integration.emi.fold.model.FoldClassifier
import allyouneed.client.integration.emi.fold.model.FoldFeature
import allyouneed.client.integration.emi.fold.model.FoldKind
import allyouneed.client.integration.emi.fold.model.FoldTree
import allyouneed.util.logger
import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.runtime.EmiReloadManager
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.Fluid
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** 主线程提取轻量特征，后台只处理不可变数据。按原始完整索引身份管理生命周期。 */
object EmiFoldIndex {
    class Catalog(val source: List<EmiStack>, val tree: FoldTree) {
        val positions = IdentityHashMap<EmiIngredient, Int>().apply { source.forEachIndexed { i, stack -> put(stack, i) } }
        private val groups = ConcurrentHashMap<String, List<EmiStack>>()
        fun members(key: String): List<EmiStack>? = tree.nodes[key]?.let { node -> groups.computeIfAbsent(key) { node.members.map { source[it] } } }
    }
    @Volatile var catalog: Catalog? = null
        private set
    private var requested: List<EmiStack>? = null
    private var generation = 0L
    var building: Boolean = false
        private set

    fun ensure() {
        val client = Minecraft.getInstance()
        if (!client.isSameThread || !EmiReloadManager.isLoaded()) return
        val source = EmiApi.getIndexStacks()
        if (requested === source) return
        requested = source
        catalog = null
        val token = ++generation
        building = true
        try {
            val hierarchy = HashMap<Class<*>, List<String>>()
            fun chain(type: Class<*>) = hierarchy.getOrPut(type) { generateSequence<Class<*>>(type) { it.superclass }.map { it.name }.toList() }
            val known = BuiltInRegistries.ITEM.keySet().mapTo(HashSet()) { it.toString() }
            source.forEach { runCatching { it.id.toString() }.getOrNull()?.let(known::add) }
            val features = source.mapIndexed { i, stack ->
                try {
                    val itemStack = stack.itemStack
                    val key = stack.key
                    val item = itemStack.item
                    val kind = when {
                        !itemStack.isEmpty -> if (item is BlockItem) FoldKind.BLOCK else FoldKind.ITEM
                        key is Fluid -> FoldKind.FLUID
                        else -> FoldKind.CUSTOM
                    }
                    val type = when (kind) { FoldKind.BLOCK -> (item as BlockItem).block.javaClass; FoldKind.ITEM -> item.javaClass; else -> key.javaClass }
                    val base = when (kind) { FoldKind.BLOCK -> Block::class.java.name; FoldKind.ITEM -> Item::class.java.name; else -> type.name }
                    val tags = when (kind) {
                        FoldKind.ITEM, FoldKind.BLOCK -> itemStack.tags.map { it.location().toString() }.sorted().toList()
                        FoldKind.FLUID -> (key as Fluid).builtInRegistryHolder().tags().map { it.location().toString() }.sorted().toList()
                        else -> emptyList()
                    }
                    FoldFeature(stack.id.toString(), kind, type.name,
                        if (kind == FoldKind.FLUID) "Fluid" else FoldFeature.guard(chain(type), base),
                        FoldFeature.classWords(chain(type)), FoldFeature.classHead(type.name), tags,
                        (item as? ArmorItem)?.equipmentSlot?.name, extractReferences(stack.nbt, known))
                } catch (e: Exception) {
                    logger.debug("Cannot classify EMI entry {}", i, e)
                    FoldFeature("unknown:entry_$i", FoldKind.CUSTOM, stack.javaClass.name, "${stack.javaClass.name}#$i", emptySet(), "")
                }
            }
            CompletableFuture.supplyAsync { FoldClassifier.build(features) }.whenComplete { tree, error ->
                client.execute {
                    if (generation != token) return@execute
                    building = false
                    if (error != null) {
                        logger.error("Unable to build EMI classification tree", error)
                    } else if (EmiReloadManager.isLoaded() && EmiApi.getIndexStacks() === source) {
                        catalog = Catalog(source, tree)
                        EmiFoldGroups.treeReady()
                    }
                }
            }
        } catch (e: Exception) {
            building = false
            logger.error("Unable to snapshot EMI classification features", e)
        }
    }

    private fun extractReferences(tag: CompoundTag?, known: Set<String>): Map<String, String> {
        val found = HashMap<List<String>, MutableSet<String>>()
        fun visit(value: Tag, path: List<String>, depth: Int) {
            if (depth > 64) return
            when (value) {
                is CompoundTag -> value.allKeys.sorted().forEach { k -> value.get(k)?.let { visit(it, path + k, depth + 1) } }
                is ListTag -> value.forEach { visit(it, path + "[]", depth + 1) }
                is StringTag -> value.asString.takeIf { it in known }?.let { found.getOrPut(path) { linkedSetOf() }.add(it) }
            }
        }
        if (tag != null) visit(tag, emptyList(), 0)
        return found.filterValues { it.size == 1 }.mapKeys { (path, _) -> path.joinToString("") { "${it.length}:$it" } }
            .mapValues { it.value.single() }
    }
}
