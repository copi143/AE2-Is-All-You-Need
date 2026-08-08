package allyouneed.logic.machine

import allyouneed.util.contains
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 某物品能否放入装配室机器槽对应给定 [MachineType]。
 *
 * Whether an item may occupy the machine assembler's machine slot for a given [MachineType].
 */
fun interface MachineItemMatcher {
    fun matches(stack: ItemStack): Boolean
}

/**
 * 接受给定物品集合（按 Item 身份，忽略 NBT）。
 *
 * Accepts any of the given items (by item identity, ignore NBT).
 */
class DefaultItemsMatcher(private val items: Set<Item>) : MachineItemMatcher {
    constructor(vararg items: Item) : this(items.toSet())

    override fun matches(stack: ItemStack): Boolean = !stack.isEmpty && stack.item in items
}

/**
 * 物品标签匹配。专用服安全：标签由数据包/模组在两侧加载；空/缺失标签不匹配。
 * Item tag matcher. Safe on dedicated servers; empty/missing tags match nothing.
 */
class ItemTagMatcher(private val tag: TagKey<Item>) : MachineItemMatcher {
    constructor(id: ResourceLocation) : this(TagKey.create(Registries.ITEM, id))

    override fun matches(stack: ItemStack): Boolean = !stack.isEmpty && stack.item in tag
}

/**
 * 多个匹配器的 OR 组合。
 * OR-combination of matchers.
 */
class AnyMatcher(private val matchers: List<MachineItemMatcher>) : MachineItemMatcher {
    constructor(vararg matchers: MachineItemMatcher) : this(matchers.toList())

    override fun matches(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return matchers.any { it.matches(stack) }
    }
}
