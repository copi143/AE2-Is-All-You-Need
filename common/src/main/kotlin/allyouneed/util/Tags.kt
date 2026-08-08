package allyouneed.util

import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

operator fun TagKey<Item>.contains(item: Item): Boolean = item.builtInRegistryHolder().`is`(this)
