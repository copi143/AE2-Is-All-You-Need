package allyouneed.api.machine

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import java.util.function.Supplier

enum class MachineEncodingMode {
    CRAFTING_GRID,
    PROCESSING_SLOTS
}

data class MachineType(
    val id: ResourceLocation,
    val displayName: Component,
    val machineItem: Supplier<Item>,
    val defaultCraftingTicks: Int = 100,
    val encodingMode: MachineEncodingMode = MachineEncodingMode.PROCESSING_SLOTS
)
