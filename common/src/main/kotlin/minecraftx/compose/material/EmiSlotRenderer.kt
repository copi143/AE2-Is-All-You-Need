package minecraftx.compose.material

import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.world.item.ItemStack

/**
 * EMI-backed slot renderer: item icons drawn through [EmiStack.render] (EMI styled), tooltip via
 * [EmiStack.getTooltip], and left / right click opening EMI recipes / uses.
 *
 * Loaded reflectively by [SlotRenderers] so a missing EMI is safe.
 */
class EmiSlotRenderer : ItemSlotRenderer {
    override fun drawStack(graphics: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        if (stack.isEmpty) return
        EmiStack.of(stack).render(graphics, x, y, 0f)
    }

    override fun getTooltip(stack: ItemStack): List<ClientTooltipComponent> {
        if (stack.isEmpty) return emptyList()
        return EmiStack.of(stack).getTooltip()
    }

    override fun onClick(stack: ItemStack, button: Int) {
        if (stack.isEmpty) return
        val emiStack = EmiStack.of(stack)
        if (button == 0) EmiApi.displayRecipes(emiStack) else EmiApi.displayUses(emiStack)
    }
}
