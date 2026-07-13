package allyouneed.api.machine

import allyouneed.Constants
import allyouneed.vanillaLocation
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

object BuiltinMachineTypes {
    val CRAFTING_TABLE: MachineType by lazy {
        MachineType(
            id = "minecraft:crafting_table".vanillaLocation(),
            displayName = Component.translatable("block.minecraft.crafting_table"),
            machineItem = { Items.CRAFTING_TABLE },
            defaultCraftingTicks = 100,
            encodingMode = MachineEncodingMode.CRAFTING_GRID
        )
    }

    val FURNACE: MachineType by lazy {
        MachineType(
            id = "minecraft:furnace".vanillaLocation(),
            displayName = Component.translatable("block.minecraft.furnace"),
            machineItem = { Items.FURNACE },
            defaultCraftingTicks = 200,
            encodingMode = MachineEncodingMode.PROCESSING_SLOTS
        )
    }

    val BLAST_FURNACE: MachineType by lazy {
        MachineType(
            id = "minecraft:blast_furnace".vanillaLocation(),
            displayName = Component.translatable("block.minecraft.blast_furnace"),
            machineItem = { Items.BLAST_FURNACE },
            defaultCraftingTicks = 100,
            encodingMode = MachineEncodingMode.PROCESSING_SLOTS
        )
    }

    val SMOKER: MachineType by lazy {
        MachineType(
            id = "minecraft:smoker".vanillaLocation(),
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
        Constants.LOG.info("Registered builtin MachineTypes")
    }
}
