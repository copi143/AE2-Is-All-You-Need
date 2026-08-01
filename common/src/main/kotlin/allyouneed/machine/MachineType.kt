package allyouneed.machine

import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * Defines a machine that a machine assembler can be configured with.
 *
 * Each machine type is bound to a vanilla [RecipeType]; encoded machine patterns are resolved
 * against that recipe type at runtime via [resolve].
 */
data class MachineType(
    val id: String,
    val name: String,
    val icon: ItemStack,
    val recipeType: RecipeType<*>,
    /** How many of the assembler's 3x3 grid slots this machine actually uses. */
    val inputSlots: Int,
    /** Whether the given item (placed into the machine slot) selects this machine. */
    val accepts: (ItemStack) -> Boolean,
    /** Resolves the output produced from the currently filled assembler grid, or null if nothing matches. */
    val resolve: (Level, CraftingContainer) -> ItemStack?,
    /** Remaining items after one operation, aligned to the container slots. */
    val remainders: (Level, CraftingContainer) -> List<ItemStack>,
)
