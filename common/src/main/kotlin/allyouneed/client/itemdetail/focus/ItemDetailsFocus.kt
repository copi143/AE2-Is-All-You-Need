package allyouneed.client.itemdetail.focus

import net.minecraft.client.Minecraft
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult

/**
 * Resolves the item the player is currently hovering over. Priority:
 *
 *  1. EMI's hovered stack (via `EmiApi.getHoveredStack`),
 *  2. JEI's ingredient list / bookmark overlay under the mouse,
 *  3. the block the player is looking at (vanilla raycast),
 *  4. the item currently held in hand.
 *
 * EMI and JEI are accessed through reflection so that the common module stays
 * loadable even when either mod is absent.
 */
object ItemDetailsFocus {

    fun hoveredStack(): ItemStack? {
        emiHovered()?.let { return it }
        jeiHovered()?.let { return it }
        targetedBlock()?.let { return it }
        return heldItem()
    }

    // ------------------------------------------------------------------
    // EMI: EmiApi.getHoveredStack(boolean) -> EmiStackInteraction.getStack()
    //      -> EmiStack.getItemStack()
    // ------------------------------------------------------------------
    private fun emiHovered(): ItemStack? = try {
        val api = Class.forName("dev.emi.emi.api.EmiApi")
        val getHovered = api.getMethod("getHoveredStack", Boolean::class.javaPrimitiveType)
        val interaction = getHovered.invoke(null, true)
        val interactionClass = Class.forName("dev.emi.emi.api.stack.EmiStackInteraction")
        val ingredient = interactionClass.getMethod("getStack").invoke(interaction)
        val stack = emiIngredientToItemStack(ingredient) ?: return null
        if (stack.isEmpty) null else stack
    } catch (e: Throwable) {
        null
    }

    private fun emiIngredientToItemStack(ingredient: Any?): ItemStack? {
        if (ingredient == null) return null
        runCatching { ingredient::class.java.getMethod("getItemStack").invoke(ingredient) as? ItemStack }.getOrNull()
            ?.let { return it }
        val getStacks = runCatching { ingredient::class.java.getMethod("getEmiStacks") }.getOrNull() ?: return null
        val stacks = getStacks.invoke(ingredient) as? List<*> ?: return null
        val first = stacks.firstOrNull() ?: return null
        return runCatching { first::class.java.getMethod("getItemStack").invoke(first) as? ItemStack }.getOrNull()
    }

    // ------------------------------------------------------------------
    // JEI: JeiRuntime.getIngredientListOverlay().getIngredientUnderMouse()
    //      JeiRuntime.getBookmarkOverlay().getItemStackUnderMouse()
    // ------------------------------------------------------------------
    private fun jeiHovered(): ItemStack? = try {
        val runtime =
            Class.forName("allyouneed.client.integration.jei.JeiRuntimeStore").getMethod("getRuntime").invoke(null)
                ?: return null
        val runtimeClass = runtime::class.java

        val overlay = runtimeClass.getMethod("getIngredientListOverlay").invoke(runtime)
        val typed = overlay::class.java.getMethod("getIngredientUnderMouse").invoke(overlay)
        if (typed is java.util.Optional<*> && typed.isPresent) {
            val ingredient = typed.get()
            val itemStack = runCatching {
                ingredient::class.java.getMethod("getItemStack").invoke(ingredient) as? java.util.Optional<*>
            }.getOrNull()?.get()
            if (itemStack is ItemStack) return itemStack
        }

        val bookmark = runCatching { runtimeClass.getMethod("getBookmarkOverlay").invoke(runtime) }.getOrNull()
        if (bookmark != null) {
            val stack =
                runCatching { bookmark::class.java.getMethod("getItemStackUnderMouse").invoke(bookmark) }.getOrNull()
            if (stack is ItemStack) return stack
        }
        null
    } catch (e: Throwable) {
        null
    }

    // ------------------------------------------------------------------
    // Vanilla: the block the player is looking at
    // ------------------------------------------------------------------
    private fun targetedBlock(): ItemStack? {
        val mc = Minecraft.getInstance()
        val hit = mc.hitResult ?: return null
        val blockHit = hit as? BlockHitResult ?: return null
        val level = mc.level ?: return null
        val pos = blockHit.blockPos
        val state = level.getBlockState(pos)
        val item = state.block.asItem()
        if (item == Items.AIR) {
            // try the block behind an attached state (signs, torches, buttons...)
            for (dir in Direction.entries) {
                val behind = pos.relative(dir)
                val behindState = level.getBlockState(behind)
                val behindItem = behindState.block.asItem()
                if (behindItem != Items.AIR) {
                    return behindItem.defaultInstance
                }
            }
            return null
        }
        return item.defaultInstance
    }

    // ------------------------------------------------------------------
    // Vanilla fallback: the item held in hand
    // ------------------------------------------------------------------
    private fun heldItem(): ItemStack? {
        val player = Minecraft.getInstance().player ?: return null
        val main = player.mainHandItem
        if (!main.isEmpty) return main
        return player.offhandItem
    }
}
