package allyouneed.platform

import allyouneed.util.PlatformHelper
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader

class ForgePlatformHelper : PlatformHelper {
    override val name = "Forge"

    override fun isModLoaded(modId: String?): Boolean {
        return ModList.get().isLoaded(modId)
    }

    override fun isDev(): Boolean {
        return !FMLLoader.isProduction()
    }
}
