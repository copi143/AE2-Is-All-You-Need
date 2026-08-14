package allyouneed.logic.machine

import allyouneed.util.MODID
import allyouneed.util.rl
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.RecipeType

/**
 * 内置配方类别（构造即注册）。
 * Built-in categories (auto-register on construct).
 */
object BuiltinMachineTypes {
    private fun matcher(defaults: Array<Item>, tagPath: String) =
        MachineItemMatcher(
            items = defaults.toSet(),
            tags = MachineItemMatcher.tags("machines/$tagPath".rl),
        )

    val CRAFTING = MachineType(
        id = MachineType.idOf(RecipeType.CRAFTING),
        name = Component.translatable("gui.$MODID.machine.crafting"),
        icon = ItemStack(Items.CRAFTING_TABLE),
        inputSlots = 9,
        machineMatcher = matcher(arrayOf(Items.CRAFTING_TABLE), "crafting"),
        recipeSource = MachineRecipes.source(RecipeType.CRAFTING),
        recipeType = RecipeType.CRAFTING,
    )

    val SMELTING = cooking(RecipeType.SMELTING, "smelting", Items.FURNACE)
    val BLASTING = cooking(RecipeType.BLASTING, "blasting", Items.BLAST_FURNACE)
    val SMOKING = cooking(RecipeType.SMOKING, "smoking", Items.SMOKER)

    private fun cooking(type: RecipeType<*>, path: String, icon: Item) = MachineType(
        id = MachineType.idOf(type),
        name = Component.translatable("gui.$MODID.machine.$path"),
        icon = ItemStack(icon),
        inputSlots = 1,
        machineMatcher = matcher(arrayOf(icon), path),
        recipeSource = MachineRecipes.source(type),
        recipeType = type,
    )

    /** 触发类加载完成注册。 */
    fun registerAll() {
        listOf(CRAFTING, SMELTING, BLASTING, SMOKING)
    }
}
