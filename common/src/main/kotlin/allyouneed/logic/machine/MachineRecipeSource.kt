package allyouneed.logic.machine

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * [MachineType] 的代码侧配方后端（如原版 [RecipeType.SMELTING]）。
 * 手动数据包配方总是先匹配；本接口为回退。
 *
 * Code-bound recipe backend for a [MachineType]. Manual datapack recipes are tried first;
 * this is the fallback.
 */
fun interface MachineRecipeSource {
    fun resolve(level: Level, container: Container): ItemStack?

    fun remainders(level: Level, container: Container): List<ItemStack> =
        List(container.containerSize) { ItemStack.EMPTY }
}

/**
 * 原版 / 已知 RecipeType 的配方源工厂。
 * Factories for vanilla / known [RecipeType] backends.
 */
object VanillaRecipeSources {
    @JvmStatic
    fun crafting(): MachineRecipeSource = object : MachineRecipeSource {
        override fun resolve(level: Level, container: Container): ItemStack? =
            MachineRecipes.resolveCrafting(level, container)

        override fun remainders(level: Level, container: Container): List<ItemStack> =
            MachineRecipes.remaindersCrafting(level, container)
    }

    @JvmStatic
    fun cooking(type: RecipeType<*>): MachineRecipeSource =
        MachineRecipeSource { level, container -> MachineRecipes.resolveCooking(type, level, container) }

    /**
     * 将数据包 `recipe_source` 字符串解析为已知后端；未知或空则 null。
     * 接受原版注册名（`minecraft:smelting`）与短别名（`smelting`）。
     *
     * Resolve a datapack `recipe_source` string to a known backend, or null if unknown / empty.
     */
    @JvmStatic
    fun fromId(id: String?): MachineRecipeSource? {
        if (id.isNullOrBlank()) return null
        val key = id.removePrefix("minecraft:")
        return when (key) {
            "crafting" -> crafting()
            "smelting" -> cooking(RecipeType.SMELTING)
            "blasting" -> cooking(RecipeType.BLASTING)
            "smoking" -> cooking(RecipeType.SMOKING)
            "campfire_cooking" -> cooking(RecipeType.CAMPFIRE_COOKING)
            else -> {
                val rl = ResourceLocation.tryParse(id) ?: return null
                val type = BuiltInRegistries.RECIPE_TYPE.get(rl) ?: return null
                // 单输入类型用 cooking 查找；合成需要 CraftingContainer
                if (type === RecipeType.CRAFTING) crafting() else cooking(type)
            }
        }
    }

    @JvmStatic
    fun recipeTypeFromId(id: String): RecipeType<*>? =
        BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.tryParse(id) ?: return null)
}
