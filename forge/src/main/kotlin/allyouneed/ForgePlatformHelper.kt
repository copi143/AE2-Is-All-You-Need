package allyouneed

import allyouneed.forge.init.ForgeKeyBindings
import allyouneed.util.PlatformHelper
import net.minecraft.client.KeyMapping
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader

class ForgePlatformHelper : PlatformHelper {
    override val name = "Forge"

    override fun isModLoaded(modId: String): Boolean {
        return ModList.get().isLoaded(modId)
    }

    override val isDev = !FMLLoader.isProduction()

    override fun registerKeyBinding(key: KeyMapping) {
        ForgeKeyBindings.keys += key
    }

    override fun onClientTick(handler: () -> Unit) {
        ForgeKeyBindings.tickHandlers += handler
    }
}
