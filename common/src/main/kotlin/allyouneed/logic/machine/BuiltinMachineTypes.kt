package allyouneed.logic.machine

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

object BuiltinMachineTypes {
    val CRAFTING_TABLE_TYPE: MachineType = MachineType(
        id = "crafting",
        name = "Crafting Table",
        icon = ItemStack(Items.CRAFTING_TABLE),
        recipeType = RecipeType.CRAFTING,
        inputSlots = 9,
        accepts = { stack -> stack.item == Items.CRAFTING_TABLE },
        resolve = { level: Level, container ->
            val recipe = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, container, level).orElse(null)
            if (recipe != null) recipe.assemble(container, level.registryAccess()) else null
        },
        remainders = { level: Level, container ->
            val recipe = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, container, level).orElse(null)
            if (recipe != null) recipe.getRemainingItems(container) else List(container.containerSize) { ItemStack.EMPTY }
        },
    )

    val FURNACE_TYPE: MachineType = MachineType(
        id = "smelting",
        name = "Furnace",
        icon = ItemStack(Items.FURNACE),
        recipeType = RecipeType.SMELTING,
        inputSlots = 1,
        accepts = { stack -> stack.item == Items.FURNACE },
        resolve = { level: Level, container ->
            val recipe = level.recipeManager.getRecipeFor(RecipeType.SMELTING, container, level).orElse(null)
            if (recipe != null) recipe.getResultItem(level.registryAccess()) else null
        },
        remainders = { _, container -> List(container.containerSize) { ItemStack.EMPTY } },
    )

    fun registerAll() {
        MachineTypeRegistry.register(CRAFTING_TABLE_TYPE)
        MachineTypeRegistry.register(FURNACE_TYPE)
    }
}
