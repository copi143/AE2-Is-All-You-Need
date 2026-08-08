package allyouneed.logic.machine

import allyouneed.util.MODID
import allyouneed.util.rl
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.RecipeType

/**
 * 代码注册的内置配方类别（构造 [MachineType] 即自动注册）。
 * Built-in categories; constructing [MachineType] auto-registers.
 */
object BuiltinMachineTypes {
    private fun ownTag(path: String) = ItemTagMatcher("machines/$path".rl)

    private fun matchers(defaults: Array<Item>, ownTagPath: String, vararg conventionTags: ResourceLocation) =
        AnyMatcher(
            buildList {
                add(DefaultItemsMatcher(*defaults))
                add(ownTag(ownTagPath))
                conventionTags.forEach { add(ItemTagMatcher(it)) }
            },
        )

    val CRAFTING: MachineType = MachineType(
        id = MachineType.idOf(RecipeType.CRAFTING),
        name = Component.translatable("gui.$MODID.machine.crafting"),
        icon = ItemStack(Items.CRAFTING_TABLE),
        inputSlots = 9,
        machineMatcher = matchers(arrayOf(Items.CRAFTING_TABLE), "crafting"),
        recipeSource = VanillaRecipeSources.crafting(),
        recipeType = RecipeType.CRAFTING,
    )

    val SMELTING: MachineType = cooking(
        recipeType = RecipeType.SMELTING,
        nameKey = "gui.$MODID.machine.smelting",
        icon = Items.FURNACE,
        tagPath = "smelting",
        defaults = arrayOf(Items.FURNACE),
    )

    val BLASTING: MachineType = cooking(
        recipeType = RecipeType.BLASTING,
        nameKey = "gui.$MODID.machine.blasting",
        icon = Items.BLAST_FURNACE,
        tagPath = "blasting",
        defaults = arrayOf(Items.BLAST_FURNACE),
    )

    val SMOKING: MachineType = cooking(
        recipeType = RecipeType.SMOKING,
        nameKey = "gui.$MODID.machine.smoking",
        icon = Items.SMOKER,
        tagPath = "smoking",
        defaults = arrayOf(Items.SMOKER),
    )

    private fun cooking(
        recipeType: RecipeType<*>,
        nameKey: String,
        icon: Item,
        tagPath: String,
        defaults: Array<Item>,
    ): MachineType {
        val id = MachineType.idOf(recipeType)
        return MachineType(
            id = id,
            name = Component.translatable(nameKey),
            icon = ItemStack(icon),
            inputSlots = 1,
            machineMatcher = matchers(defaults, tagPath),
            recipeSource = VanillaRecipeSources.cooking(recipeType),
            recipeType = recipeType,
        )
    }

    /** 触发类加载以完成内置类型构造注册。Forces class init / auto-registration. */
    fun registerAll() {
        // 访问字段以确保 object 初始化
        listOf(CRAFTING, SMELTING, BLASTING, SMOKING)
    }
}
