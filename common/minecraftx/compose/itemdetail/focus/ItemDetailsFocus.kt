package minecraftx.compose.itemdetail.focus

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.BlockHitResult

/**
 * Resolves the item the player is currently hovering over. Priority:
 *
 *  1. EMI's hovered stack (via `EmiApi.getHoveredStack`),
 *  2. JEI's ingredient list / bookmark overlay under the mouse,
 *  3. the hovered slot of an open container screen,
 *  4. the block the player is looking at (vanilla raycast),
 *  5. the item currently held in hand.
 *
 * EMI and JEI are accessed through reflection so that the common module stays
 * loadable even when either mod is absent.
 */
object ItemDetailsFocus {

    fun hoveredStack(): ItemStack? {
        emiHovered()?.let { return it }
        jeiHovered()?.let { return it }
        containerSlot()?.let { return it }
        targetedBlock()?.let { return it }
        return heldItem()
    }

    private fun containerSlot(): ItemStack? {
        val screen = Minecraft.getInstance().screen as? AbstractContainerScreen<*>
            ?: return null
        val slot = runCatching {
            val field = AbstractContainerScreen::class.java
                .getDeclaredField("hoveredSlot")
            field.isAccessible = true
            field.get(screen)
        }.getOrNull() as? Slot ?: return null
        val stack = slot.item
        return if (stack.isEmpty) null else stack
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
        val typed = overlay::class.java.methods
            .firstOrNull { it.name == "getIngredientUnderMouse" && it.parameterCount == 0 }
            ?.invoke(overlay)
        itemStackFromJei(typed)?.let { return it }

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

    private fun itemStackFromJei(typed: Any?): ItemStack? {
        if (typed == null) return null
        val value = if (typed is java.util.Optional<*>) typed.orElse(null) else typed
        if (value is ItemStack) return value.takeUnless { it.isEmpty }
        runCatching { value?.javaClass?.getMethod("getItemStack")?.invoke(value) }.getOrNull()?.let { inner ->
            val stack = if (inner is java.util.Optional<*>) inner.orElse(null) else inner
            if (stack is ItemStack && !stack.isEmpty) return stack
        }
        runCatching { value?.javaClass?.getMethod("getIngredient")?.invoke(value) }.getOrNull()?.let { inner ->
            val stack = if (inner is java.util.Optional<*>) inner.orElse(null) else inner
            if (stack is ItemStack && !stack.isEmpty) return stack
        }
        return null
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
            val behind = pos.relative(blockHit.direction.opposite)
            val behindItem = level.getBlockState(behind).block.asItem()
            return if (behindItem == Items.AIR) null else behindItem.defaultInstance
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
