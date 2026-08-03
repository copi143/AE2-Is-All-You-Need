package allyouneed

import allyouneed.cell.CraftingStorage
import allyouneed.cell.EnergyCell
import allyouneed.client.ForgeCreativeTab
import allyouneed.forge.init.ForgeBlocks
import allyouneed.forge.init.ForgeItems
import allyouneed.forge.init.ForgeMenus
import allyouneed.forge.init.GTAsyncCrafting
import allyouneed.util.MODID
import allyouneed.util.Services
import allyouneed.util.logger
import net.minecraftforge.fml.common.Mod
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(MODID)
class ExampleMod {
    init {
        logger.info("Hello Forge world from Kotlin!")

        EnergyCell.registerSelfPoweredBEType()
        EnergyCell.entries.forEach { it.registerBEType() }
        CraftingStorage.registerBEType()

        AllRegistries.blocks.forEach { entry ->
            ForgeBlocks.BLOCKS.register(entry.id().path) { entry.block() }
            ForgeItems.ITEMS.register(entry.id().path) { entry.asItem() }
        }

        EnergyCell.entries.filter { !it.selfPowered }.forEach { cell ->
            ForgeBlocks.BLOCK_ENTITIES.register(cell.blockId.path) { cell.blockEntityType }
        }
        ForgeBlocks.BLOCK_ENTITIES.register("self_powered_energy_cell") { EnergyCell.selfPoweredBlockEntityType }
        ForgeBlocks.BLOCK_ENTITIES.register("crafting_storage") { CraftingStorage.blockEntityType }

        AllRegistries.items.forEach { entry ->
            ForgeItems.ITEMS.register(entry.id().path) { entry.asItem() }
        }

        // Use KotlinForForge's MOD_BUS instead of FMLJavaModLoadingContext
        ForgeItems.register(MOD_BUS)
        ForgeBlocks.register(MOD_BUS)
        ForgeMenus.register(MOD_BUS)
        ForgeCreativeTab.register(MOD_BUS)

        // GTCEu host flavour: the common host block is replaced by the GT multiblock machine.
        if (Services.platform.isModLoaded("gtceu")) {
            GTAsyncCrafting.init(MOD_BUS)
        }

        CommonObject.init()

        // Initialize the scripting system
        try {
            val configDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
            allyouneed.script.ScriptDsl.init(configDir)
            logger.info("Script system initialized")
        } catch (e: Exception) {
            logger.error("Failed to initialize script system", e)
        }
    }
}
