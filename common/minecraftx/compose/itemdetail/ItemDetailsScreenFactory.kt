package minecraftx.compose.itemdetail

import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack

/**
 * Always produces the Compose renderer. EMI interaction (tooltip / recipes / uses) is handled
 * inside the Compose screen's [minecraftx.compose.material.ItemSlot] with a vanilla fallback,
 * so no separate EMI renderer is needed.
 */
object ItemDetailsScreenFactory {
    fun create(stack: ItemStack): Screen = ComposeItemDetailsScreen(ItemDetails(stack))
}
