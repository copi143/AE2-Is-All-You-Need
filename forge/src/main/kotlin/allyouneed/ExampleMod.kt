package allyouneed

import allyouneed.client.ForgeCreativeTab
import allyouneed.forge.init.ForgeBlocks
import allyouneed.forge.init.ForgeItems
import allyouneed.forge.init.ForgeMenus
import allyouneed.util.MODID
import allyouneed.util.logger
import net.minecraftforge.fml.common.Mod
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(MODID)
class ExampleMod {
    init {
        logger.info("Hello Forge world from Kotlin!")

        allyouneed.energy.EnergyCell.entries.forEach { it.registerBEType() }

        AllRegistries.blocks.forEach { entry ->
            ForgeBlocks.BLOCKS.register(entry.id().path) { entry.block() }
            ForgeItems.ITEMS.register(entry.id().path) { entry.asItem() }
        }

        allyouneed.energy.EnergyCell.entries.forEach { cell ->
            ForgeBlocks.BLOCK_ENTITIES.register(cell.blockId.path) { cell.blockEntityType }
        }

        AllRegistries.items.forEach { entry ->
            ForgeItems.ITEMS.register(entry.id().path) { entry.asItem() }
        }

        // Use KotlinForForge's MOD_BUS instead of FMLJavaModLoadingContext
        ForgeItems.register(MOD_BUS)
        ForgeBlocks.register(MOD_BUS)
        ForgeMenus.register(MOD_BUS)
        ForgeCreativeTab.register(MOD_BUS)
        CommonObject.init()
    }
}
