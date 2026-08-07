package allyouneed.client.itemdetail

import allyouneed.client.integration.emi.EmiItemDetailsScreen
import allyouneed.client.integration.jei.JeiItemDetailsScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack

/**
 * Picks the best available renderer for the item-details screen:
 * EMI when present, JEI as a fallback, otherwise the vanilla renderer.
 */
object ItemDetailsScreenFactory {

    private val emiAvailable = runCatching { Class.forName("dev.emi.emi.api.EmiApi") }.isSuccess
    private val jeiAvailable = runCatching { Class.forName("mezz.jei.api.JeiPlugin") }.isSuccess

    fun create(stack: ItemStack): Screen {
        val details = ItemDetails(stack)
        return when {
            emiAvailable -> EmiItemDetailsScreen(details)
            jeiAvailable -> JeiItemDetailsScreen(details)
            else -> ItemDetailsScreen(details)
        }
    }
}
