package allyouneed.client.group

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.function.Supplier

class CreativeTabGroup(
    val id: ResourceLocation,
    val displayName: Component,
    val icon: Supplier<ItemStack>,
) {
    val tabIds: MutableSet<ResourceLocation> = mutableSetOf()

    fun addTab(tabId: ResourceLocation): CreativeTabGroup {
        tabIds.add(tabId)
        return this
    }

    fun addTab(namespace: String, path: String): CreativeTabGroup {
        tabIds.add(ResourceLocation(namespace, path))
        return this
    }

    fun containsTab(tabId: ResourceLocation): Boolean = tabIds.contains(tabId)
}
