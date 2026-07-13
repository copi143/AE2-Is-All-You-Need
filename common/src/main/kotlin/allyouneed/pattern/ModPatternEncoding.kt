package allyouneed.pattern

import allyouneed.api.machine.MachineTypeRegistry
import appeng.api.stacks.GenericStack
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object ModPatternEncoding {

    fun encodeMachinePattern(
        machineTypeId: ResourceLocation,
        inputs: Array<GenericStack>,
        outputs: Array<GenericStack>,
    ): ItemStack {
        val item = ModItems.MACHINE_PATTERN
        return item.encode(machineTypeId, inputs, outputs)
    }

    fun encodePseudoPattern(
        displayName: Component?, icon: ItemStack?, inputs: Array<GenericStack?>
    ): ItemStack {
        val item = ModItems.PSEUDO_PATTERN
        return item.encode(displayName, icon, inputs)
    }

    /**
     * Helper: given a potential "target machine" stack (the item the player put to indicate machine type),
     * return the MachineType id if it matches a registered one.
     */
    fun getMachineTypeIdFromItem(stack: ItemStack): ResourceLocation? {
        if (stack.isEmpty) return null
        for (mt in MachineTypeRegistry.getAll()) {
            if (mt.machineItem.get() == stack.item) {
                return mt.id
            }
        }
        return null
    }
}
