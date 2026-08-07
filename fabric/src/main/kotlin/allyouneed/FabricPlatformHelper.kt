package allyouneed

import allyouneed.util.PlatformHelper
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping

class FabricPlatformHelper : PlatformHelper {
    override val name = "Fabric"

    override fun isModLoaded(modId: String): Boolean {
        return FabricLoader.getInstance().isModLoaded(modId)
    }

    override val isDev: Boolean = FabricLoader.getInstance().isDevelopmentEnvironment

    override fun registerKeyBinding(key: KeyMapping) {
        KeyBindingHelper.registerKeyBinding(key)
    }

    override fun onClientTick(handler: () -> Unit) {
        ClientTickEvents.END_CLIENT_TICK.register { handler() }
    }
}
