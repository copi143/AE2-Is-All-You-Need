package allyouneed

import allyouneed.forge.init.ForgeBlocks
import allyouneed.forge.init.ForgeItems
import allyouneed.forge.init.ForgeMenus
import net.minecraftforge.fml.common.Mod
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(Constants.MOD_ID)
class ExampleMod() {
    init {
        Constants.LOG.info("Hello Forge world from Kotlin!")
        // Use KotlinForForge's MOD_BUS instead of FMLJavaModLoadingContext
        ForgeItems.register(MOD_BUS)
        ForgeBlocks.register(MOD_BUS)
        ForgeMenus.register(MOD_BUS)
        CommonObject.init()
    }
}