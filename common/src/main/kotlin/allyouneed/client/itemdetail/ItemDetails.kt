package allyouneed.client.itemdetail

import allyouneed.client.itemdetail.styling.DetailsStyling.formatFloat
import allyouneed.client.itemdetail.styling.DetailsStyling.kv
import allyouneed.client.itemdetail.styling.DetailsStyling.line
import allyouneed.client.itemdetail.styling.DetailsStyling.section
import allyouneed.client.itemdetail.styling.DetailsStyling.tag
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.nbt.NbtUtils

/**
 * Data model for the item-details screen. Collects the block/item properties,
 * the implemented Java interfaces, the registered tags and the NBT into a
 * flat, renderer-agnostic list of sections.
 */
class ItemDetails(val stack: ItemStack) {

    /** The block behind the held item, may be air for non-block items. */
    val block: Block = Block.byItem(stack.item)

    val title: Component = stack.hoverName.copy()

    val sections: List<Section> = buildList {
        add(basicSection())
        add(statePropertiesSection())
        add(interfacesSection("实现的 Java 接口（方块）", block))
        add(interfacesSection("实现的 Java 接口（物品）", stack.item))
        add(tagsSection("物品 Tags", itemTags()))
        add(tagsSection("方块 Tags", blockTags()))
        add(nbtSection())
    }

    data class Section(val title: Component, val lines: List<Component>)

    private fun basicSection(): Section {
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item) ?: ResourceLocation("minecraft", "air")
        val blockId = BuiltInRegistries.BLOCK.getKey(block)
        val state = block.defaultBlockState()
        val lines = buildList {
            add(kv("物品注册名：", itemId))
            add(kv("方块注册名：", blockId?.toString() ?: "—"))
            add(kv("物品类：", stack.item.javaClass.name))
            add(kv("方块类：", block.javaClass.name))
            add(kv("方块实体：", if (block is EntityBlock) "有" else "无"))
            add(kv("硬度：", runCatching { state.getDestroySpeed(null, BlockPos.ZERO) }.getOrNull()?.let(::formatFloat) ?: "—"))
            add(kv("爆炸抗性：", formatFloat(block.getExplosionResistance())))
            add(kv("摩擦系数：", formatFloat(block.getFriction())))
            add(kv("跳跃系数：", formatFloat(block.getJumpFactor())))
            add(kv("移动系数：", formatFloat(block.getSpeedFactor())))
            add(kv("光照等级：", runCatching { state.getLightEmission() }.getOrNull() ?: "—"))
            add(kv("声音音量/音调：", runCatching {
                val s = state.getSoundType()
                "${formatFloat(s.getVolume())}/${formatFloat(s.getPitch())}"
            }.getOrNull() ?: "—"))
            add(kv("是否空气：", if (state.isAir) "是" else "否"))
        }
        return Section(section("基本信息"), lines)
    }

    private fun statePropertiesSection(): Section {
        val properties = block.stateDefinition.properties
        val lines = if (properties.isEmpty()) {
            listOf(line("（无）"))
        } else {
            properties.map { prop -> kv("${prop.name}：", describeProperty(prop)) }
        }
        return Section(section("方块状态属性"), lines)
    }

    private fun describeProperty(prop: Property<*>): String {
        @Suppress("UNCHECKED_CAST")
        val typed = prop as Property<Comparable<*>>
        return typed.possibleValues.joinToString(" | ") { typed.getName(it) }
    }

    private fun interfacesSection(titleText: String, obj: Any): Section {
        val list = interfaceNames(obj.javaClass)
        val lines = if (list.isEmpty()) listOf(line("（无）")) else list.map { line(it) }
        return Section(section(titleText), lines)
    }

    private fun interfaceNames(clazz: Class<*>): List<String> {
        val result = linkedSetOf<String>()
        var current: Class<*>? = clazz
        while (current != null) {
            collectInterfaces(current, result)
            current = current.superclass
        }
        return result.toList()
    }

    private fun collectInterfaces(clazz: Class<*>, out: MutableSet<String>) {
        for (i in clazz.interfaces) {
            out += i.name
            collectInterfaces(i, out)
        }
    }

    private fun itemTags(): List<TagKey<*>> = tagsOf(BuiltInRegistries.ITEM, stack.item)

    private fun blockTags(): List<TagKey<*>> = tagsOf(BuiltInRegistries.BLOCK, block)

    private fun <T> tagsOf(registry: net.minecraft.core.Registry<T>, value: T): List<TagKey<T>> {
        val key = registry.getResourceKey(value).orElse(null) ?: return emptyList()
        val holder: Holder<T> = registry.getHolderOrThrow(key)
        return holder.tags().toList().sortedBy { it.location().toString() }
    }

    private fun tagsSection(titleText: String, tags: List<TagKey<*>>): Section {
        val lines = if (tags.isEmpty()) {
            listOf(line("（无）"))
        } else {
            tags.map { tag(it.location().toString()) }
        }
        return Section(section(titleText), lines)
    }

    private fun nbtSection(): Section {
        val tag = stack.tag ?: return Section(section("NBT"), listOf(line("（无 NBT）")))
        val pretty = NbtUtils.prettyPrint(tag)
        val lines = pretty.split("\n").map { line(it) }
        return Section(section("NBT"), lines)
    }
}
