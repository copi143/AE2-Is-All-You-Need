package allyouneed.api.machine

import allyouneed.rlMC
import allyouneed.util.logger
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

object BuiltinMachineTypes {
    val CRAFTING_TABLE: MachineType by lazy {
        MachineType(
            id = "crafting_table".rlMC,
            displayName = Component.translatable("block.minecraft.crafting_table"),
            machineItem = { Items.CRAFTING_TABLE },
            defaultCraftingTicks = 100,
            encodingMode = MachineEncodingMode.CRAFTING_GRID
        )
    }

    val FURNACE: MachineType by lazy {
        MachineType(
            id = "furnace".rlMC,
            displayName = Component.translatable("block.minecraft.furnace"),
            machineItem = { Items.FURNACE },
            defaultCraftingTicks = 200,
            encodingMode = MachineEncodingMode.PROCESSING_SLOTS
        )
    }

    val BLAST_FURNACE: MachineType by lazy {
        MachineType(
            id = "blast_furnace".rlMC,
            displayName = Component.translatable("block.minecraft.blast_furnace"),
            machineItem = { Items.BLAST_FURNACE },
            defaultCraftingTicks = 100,
            encodingMode = MachineEncodingMode.PROCESSING_SLOTS
        )
    }

    val SMOKER: MachineType by lazy {
        MachineType(
            id = "smoker".rlMC,
            displayName = Component.translatable("block.minecraft.smoker"),
            machineItem = { Items.SMOKER },
            defaultCraftingTicks = 100,
            encodingMode = MachineEncodingMode.PROCESSING_SLOTS
        )
    }

    fun registerAll() {
        MachineTypeRegistry.register(CRAFTING_TABLE)
        MachineTypeRegistry.register(FURNACE)
        MachineTypeRegistry.register(BLAST_FURNACE)
        MachineTypeRegistry.register(SMOKER)
        logger.info("Registered builtin MachineTypes")
    }
}
