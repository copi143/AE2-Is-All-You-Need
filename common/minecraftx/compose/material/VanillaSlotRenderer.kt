package minecraftx.compose.material

import allyouneed.client.integration.jei.JeiRuntimeStore
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

/**
 * Fallback renderer used when EMI is not installed: vanilla item drawing / tooltip plus (reflected)
 * JEI recipe lookup on click.
 */
class VanillaSlotRenderer : ItemSlotRenderer {
    private val font = Minecraft.getInstance().font

    override fun drawStack(graphics: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        if (stack.isEmpty) return
        graphics.renderItem(stack, x, y)
        graphics.renderItemDecorations(font, stack, x, y)
    }

    override fun getTooltip(stack: ItemStack): List<ClientTooltipComponent> {
        if (stack.isEmpty) return emptyList()
        val minecraft = Minecraft.getInstance()
        val flag = if (minecraft.options.advancedItemTooltips) TooltipFlag.ADVANCED else TooltipFlag.NORMAL
        return stack.getTooltipLines(minecraft.player, flag)
            .map { ClientTooltipComponent.create(it.getVisualOrderText()) }
    }

    override fun onClick(stack: ItemStack, button: Int) {
        if (stack.isEmpty) return
        JeiClicker.showRecipes(stack, button)
    }
}

/**
 * Reflected JEI entry point. Left click opens recipes (OUTPUT focus), right click opens uses (INPUT
 * focus). All lookups are guarded so a missing JEI is a silent no-op.
 */
object JeiClicker {
    private val available = runCatching { Class.forName("mezz.jei.api.runtime.IJeiRuntime") }.isSuccess

    fun showRecipes(stack: ItemStack, button: Int) {
        if (!available) return
        val runtime = JeiRuntimeStore.runtime ?: return
        try {
            val recipesGui = runtime.javaClass.getMethod("getRecipesGui").invoke(runtime)
            val focus = createFocus(runtime, stack, button) ?: return
            recipesGui.javaClass.methods
                .first { it.name == "show" && it.parameterCount == 1 }
                .invoke(recipesGui, focus)
        } catch (_: Throwable) {
        }
    }

    private fun createFocus(runtime: Any, stack: ItemStack, button: Int): Any? {
        val helpers = runCatching { runtime.javaClass.getMethod("getJeiHelpers").invoke(runtime) }
            .getOrNull() ?: return null
        val focusFactory = runCatching { helpers.javaClass.getMethod("getFocusFactory").invoke(helpers) }
            .getOrNull() ?: return null
        val ingredientManager = runCatching { runtime.javaClass.getMethod("getIngredientManager").invoke(runtime) }
            .getOrNull() ?: return null
        val ingredientType = runCatching {
            val optional = ingredientManager.javaClass
                .getMethod("getIngredientType", Class::class.java)
                .invoke(ingredientManager, ItemStack::class.java)
            optional.javaClass.getMethod("get").invoke(optional)
        }.getOrNull() ?: return null
        val role = runCatching { Class.forName("mezz.jei.api.recipe.RecipeIngredientRole") }.getOrNull() ?: return null
        val roleEnum = role.enumConstants.firstOrNull {
            (it as Enum<*>).name == (if (button == 0) "OUTPUT" else "INPUT")
        } ?: return null
        return runCatching {
            focusFactory.javaClass.methods
                .first { it.name == "createFocus" && it.parameterCount == 3 }
                .invoke(focusFactory, roleEnum, ingredientType, stack)
        }.getOrNull()
    }
}
