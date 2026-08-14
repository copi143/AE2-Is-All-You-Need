package allyouneed.logic.machine

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * [MachineType] 的代码侧配方回退（手动配方之后）。
 * Code-side recipe fallback after manual recipes.
 */
fun interface MachineRecipeSource {
    fun resolve(level: Level, container: Container): ItemStack?

    fun remainders(level: Level, container: Container): List<ItemStack> =
        List(container.containerSize) { ItemStack.EMPTY }
}

/**
 * 原版形态配方解析 + recipe_source 工厂。
 * Vanilla-shaped resolve helpers and recipe_source factory.
 */
object MachineRecipes {
    fun resolveCrafting(level: Level, container: Container): ItemStack? {
        if (container !is CraftingContainer) return null
        val recipe = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, container, level).orElse(null)
            ?: return null
        return recipe.assemble(container, level.registryAccess())
    }

    fun remaindersCrafting(level: Level, container: Container): List<ItemStack> {
        if (container !is CraftingContainer) {
            return List(container.containerSize) { ItemStack.EMPTY }
        }
        val recipe = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, container, level).orElse(null)
            ?: return List(container.containerSize) { ItemStack.EMPTY }
        return recipe.getRemainingItems(container)
    }

    fun resolveCooking(type: RecipeType<*>, level: Level, container: Container): ItemStack? {
        @Suppress("UNCHECKED_CAST")
        val recipeType = type as RecipeType<Recipe<Container>>
        val recipe = level.recipeManager.getRecipeFor(recipeType, container, level).orElse(null)
            ?: return null
        return recipe.getResultItem(level.registryAccess())
    }

    fun source(type: RecipeType<*>): MachineRecipeSource =
        if (type === RecipeType.CRAFTING) {
            object : MachineRecipeSource {
                override fun resolve(level: Level, container: Container) = resolveCrafting(level, container)
                override fun remainders(level: Level, container: Container) = remaindersCrafting(level, container)
            }
        } else {
            MachineRecipeSource { level, container -> resolveCooking(type, level, container) }
        }

    /** 解析数据包 `recipe_source`；未知则 null。 */
    fun source(id: String?): MachineRecipeSource? {
        if (id.isNullOrBlank()) return null
        val key = id.removePrefix("minecraft:")
        val type = when (key) {
            "crafting" -> RecipeType.CRAFTING
            "smelting" -> RecipeType.SMELTING
            "blasting" -> RecipeType.BLASTING
            "smoking" -> RecipeType.SMOKING
            "campfire_cooking" -> RecipeType.CAMPFIRE_COOKING
            else -> {
                val rl = ResourceLocation.tryParse(id) ?: return null
                BuiltInRegistries.RECIPE_TYPE.get(rl) ?: return null
            }
        }
        return source(type)
    }

    fun recipeType(id: String?): RecipeType<*>? {
        if (id.isNullOrBlank()) return null
        val key = id.removePrefix("minecraft:")
        return when (key) {
            "crafting" -> RecipeType.CRAFTING
            "smelting" -> RecipeType.SMELTING
            "blasting" -> RecipeType.BLASTING
            "smoking" -> RecipeType.SMOKING
            "campfire_cooking" -> RecipeType.CAMPFIRE_COOKING
            else -> {
                val rl = ResourceLocation.tryParse(id) ?: return null
                BuiltInRegistries.RECIPE_TYPE.get(rl)
            }
        }
    }
}
