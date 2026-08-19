package allyouneed

import allyouneed.cell.CraftingStorage
import allyouneed.cell.EnergyCell
import allyouneed.client.ForgeCreativeTab
import allyouneed.forge.init.ForgeBlocks
import allyouneed.forge.init.ForgeItems
import allyouneed.forge.init.ForgeMenus
import allyouneed.forge.init.GTAsyncCrafting
import allyouneed.forge.init.GTAEPowerHatch
import allyouneed.logic.script.ScriptDsl
import allyouneed.util.MODID
import allyouneed.util.logger
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(MODID)
class ForgeMain {
    init {
        logger.info("Initializing...")

        EnergyCell.registerSelfPoweredBEType()
        EnergyCell.entries.forEach { it.registerBEType() }
        CraftingStorage.registerBEType()

        AllRegistries.blocks.forEach { entry ->
            ForgeBlocks.BLOCKS.register(entry.id().path) { entry.block() }
            ForgeItems.ITEMS.register(entry.id().path) { entry.asItem() }
        }

        EnergyCell.entries.filter { !it.isSelfPowered }.forEach { cell ->
            ForgeBlocks.BLOCK_ENTITIES.register(cell.blockId.path) { cell.blockEntityType }
        }
        ForgeBlocks.BLOCK_ENTITIES.register("self_powered_energy_cell") { EnergyCell.selfPoweredBlockEntityType }
        ForgeBlocks.BLOCK_ENTITIES.register("crafting_storage") { CraftingStorage.blockEntityType }

        AllRegistries.items.forEach { entry ->
            ForgeItems.ITEMS.register(entry.id().path) { entry.asItem() }
        }

        // AE2 key types must be registered during the ae2:keytypes registry's RegisterEvent, before
        // Forge freezes that registry. FMLCommonSetupEvent (Main.commonSetup) is already too late:
        // ForgeRegistry.register throws "The object ... is being added too late".
        MOD_BUS.addListener { event: RegisterEvent ->
            if (event.registryKey == ResourceLocation("ae2", "keytypes")) {
                Main.registerAEKeyTypes()
            }
        }

        // Use KotlinForForge's MOD_BUS instead of FMLJavaModLoadingContext
        ForgeItems.register(MOD_BUS)
        ForgeBlocks.register(MOD_BUS)
        ForgeMenus.register(MOD_BUS)
        ForgeCreativeTab.register(MOD_BUS)

        // GTCEu host flavour: the common host block is replaced by the GT multiblock machine.
        if (Platform.isModLoaded("gtceu")) {
            GTAsyncCrafting.init(MOD_BUS)
            GTAEPowerHatch.init(MOD_BUS)
        }

        Main.init()

        // Initialize the scripting system
        try {
            val configDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
            ScriptDsl.init(configDir)
            logger.info("Script system initialized")
        } catch (e: Exception) {
            logger.error("Failed to initialize script system", e)
        }
    }
}
