package allyouneed.logic.machine

import allyouneed.util.contains
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 机器槽匹配：物品集合 ∪ 标签集合（OR）。
 * Machine slot matcher: items ∪ tags (OR).
 */
class MachineItemMatcher(
    val items: Set<Item> = emptySet(),
    val tags: Set<TagKey<Item>> = emptySet(),
) {
    constructor(vararg items: Item) : this(items.toSet(), emptySet())

    fun matches(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item
        if (item in items) return true
        return tags.any { item in it }
    }

    companion object {
        fun tags(vararg ids: ResourceLocation): Set<TagKey<Item>> =
            ids.map { TagKey.create(Registries.ITEM, it) }.toSet()

        fun of(items: Collection<Item>, tagIds: Collection<ResourceLocation>): MachineItemMatcher =
            MachineItemMatcher(items.toSet(), tags(*tagIds.toTypedArray()))
    }
}
