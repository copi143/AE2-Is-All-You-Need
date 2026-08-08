package allyouneed.logic.machine

import net.minecraft.world.Container
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * 原版形态配方类型的共用 resolve / remainder 辅助。
 * Shared resolve/remainder helpers for vanilla-shaped recipe types.
 */
object MachineRecipes {
    @JvmStatic
    fun resolveCrafting(level: Level, container: Container): ItemStack? {
        if (container !is CraftingContainer) return null
        val recipe = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, container, level).orElse(null) ?: return null
        return recipe.assemble(container, level.registryAccess())
    }

    @JvmStatic
    fun remaindersCrafting(level: Level, container: Container): List<ItemStack> {
        if (container !is CraftingContainer) {
            return List(container.containerSize) { ItemStack.EMPTY }
        }
        val recipe =
            level.recipeManager.getRecipeFor(RecipeType.CRAFTING, container, level).orElse(null) ?: return List(
                container.containerSize
            ) { ItemStack.EMPTY }
        return recipe.getRemainingItems(container)
    }

    /**
     * 烹饪类配方只读 [Container] 的 0 号槽。
     * Cooking recipes only read slot 0 of a [Container].
     */
    @JvmStatic
    fun resolveCooking(type: RecipeType<*>, level: Level, container: Container): ItemStack? {
        @Suppress("UNCHECKED_CAST") val recipeType = type as RecipeType<Recipe<Container>>
        val recipe = level.recipeManager.getRecipeFor(recipeType, container, level).orElse(null) ?: return null
        return recipe.getResultItem(level.registryAccess())
    }

    @JvmStatic
    fun remaindersEmpty(container: Container): List<ItemStack> = List(container.containerSize) { ItemStack.EMPTY }
}
