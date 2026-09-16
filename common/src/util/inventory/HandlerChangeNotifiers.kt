package allyouneed.util.inventory

import net.minecraft.world.item.ItemStack

object HandlerChangeNotifiers {
    @JvmStatic
    fun onInsert(handler: Any, stack: ItemStack, simulate: Boolean, leftover: ItemStack?) {
        if (simulate || leftover == null) return
        if (leftover.count < stack.count) InventoryWatchers.notifyHandler(handler)
    }

    @JvmStatic
    fun onExtract(handler: Any, simulate: Boolean, extracted: ItemStack?) {
        if (simulate || extracted == null || extracted.isEmpty) return
        InventoryWatchers.notifyHandler(handler)
    }

    @JvmStatic
    fun onSlotSet(handler: Any) {
        InventoryWatchers.notifyHandler(handler)
    }
}
