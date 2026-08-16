package allyouneed.client.integration.jei

import allyouneed.pattern.term.PatternEncodingTransfer
import allyouneed.pattern.term.UnifiedPatternEncodingTermMenu
import appeng.core.definitions.AEParts
import appeng.integration.modules.jei.transfer.EncodePatternTransferHandler
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.handlers.IGuiContainerHandler
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.transfer.IRecipeTransferError
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper
import mezz.jei.api.registration.IGuiHandlerRegistration
import mezz.jei.api.registration.IRecipeTransferRegistration
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.renderer.Rect2i
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import java.util.Optional

@JeiPlugin
class MyJeiPlugin : IModPlugin {

    companion object {
        private const val IMAGE_WIDTH = 195
        private const val IMAGE_HEIGHT = 136
        private const val SIDEBAR_WIDTH = 80
        private const val SIDEBAR_PADDING = 2
    }

    override fun getPluginUid(): ResourceLocation {
        return ResourceLocation("allyouneed", "jei_plugin")
    }

    override fun onRuntimeAvailable(runtime: IJeiRuntime) {
        JeiRuntimeStore.runtime = runtime
        runtime.ingredientManager.removeIngredientsAtRuntime(
            VanillaTypes.ITEM_STACK,
            listOf(ItemStack(AEParts.PATTERN_ENCODING_TERMINAL)),
        )
    }

    override fun onRuntimeUnavailable() {
        JeiRuntimeStore.runtime = null
    }

    override fun registerRecipeTransferHandlers(registration: IRecipeTransferRegistration) {
        registration.addUniversalRecipeTransferHandler(
            UnifiedEncodePatternTransferHandler(registration.transferHelper),
        )
    }

    override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
        registration.addGuiContainerHandler(
            CreativeModeInventoryScreen::class.java,
            object : IGuiContainerHandler<CreativeModeInventoryScreen> {
                override fun getGuiExtraAreas(screen: CreativeModeInventoryScreen): List<Rect2i> {
                    val left = (screen.width - IMAGE_WIDTH) / 2
                    val top = (screen.height - IMAGE_HEIGHT) / 2
                    val sidebarX = left - SIDEBAR_WIDTH - SIDEBAR_PADDING
                    val sidebarY = top

                    return listOf(
                        Rect2i(sidebarX, sidebarY, SIDEBAR_WIDTH, IMAGE_HEIGHT)
                    )
                }
            }
        )
    }
}

private class UnifiedEncodePatternTransferHandler(
    helper: IRecipeTransferHandlerHelper,
) : IRecipeTransferHandler<UnifiedPatternEncodingTermMenu, Any> {

    private val delegate = EncodePatternTransferHandler(
        UnifiedPatternEncodingTermMenu.TYPE,
        UnifiedPatternEncodingTermMenu::class.java,
        helper,
    )

    override fun getContainerClass(): Class<out UnifiedPatternEncodingTermMenu> =
        UnifiedPatternEncodingTermMenu::class.java

    override fun getMenuType(): Optional<MenuType<UnifiedPatternEncodingTermMenu>> =
        Optional.of(UnifiedPatternEncodingTermMenu.TYPE)

    override fun getRecipeType(): mezz.jei.api.recipe.RecipeType<Any>? = null

    override fun transferRecipe(
        menu: UnifiedPatternEncodingTermMenu,
        recipe: Any,
        recipeSlots: IRecipeSlotsView,
        player: Player,
        maxTransfer: Boolean,
        doTransfer: Boolean,
    ): IRecipeTransferError? {
        val error = delegate.transferRecipe(menu, recipe, recipeSlots, player, maxTransfer, doTransfer)
        if (doTransfer && (error == null || error.type == IRecipeTransferError.Type.COSMETIC)) {
            PatternEncodingTransfer.afterFill(menu, recipe as? Recipe<*>)
        }
        return error
    }
}
