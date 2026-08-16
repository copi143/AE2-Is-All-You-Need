package allyouneed.pattern.term

import allyouneed.logic.machine.MachineType
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType

object PatternEncodingTransfer {

    fun afterFill(menu: UnifiedPatternEncodingTermMenu, recipe: Recipe<*>?) {
        val machineType = recipe?.type?.let { MachineType.byRecipeType(it) }
        when {
            machineType != null -> {
                menu.setKind(EncodingKind.MACHINE)
                menu.setMachineIndex(MachineType.indexById(machineType.id))
            }
            menu.kind != EncodingKind.PROBABILITY -> {
                menu.setKind(EncodingKind.PROCESSING)
            }
        }
    }
}
