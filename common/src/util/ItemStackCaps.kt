package allyouneed.util

import net.minecraft.world.item.ItemStack

object ItemStackCaps {
    @JvmField
    var hasCaps: (ItemStack) -> Boolean = { false }
}
