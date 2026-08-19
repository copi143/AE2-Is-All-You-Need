package allyouneed

import allyouneed.forge.init.ForgeKeyBindings
import allyouneed.util.PlatformHelper
import appeng.api.config.PowerUnits
import com.gregtechceu.gtceu.api.capability.compat.FeCompat
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

    override fun energyUnitRatio(id: String): Double {
        return when (id) {
            "forge" -> PowerUnits.AE.convertTo(PowerUnits.FE, 1.0)
            "gtceu" -> PowerUnits.AE.convertTo(PowerUnits.FE, 1.0) / FeCompat.ratio(false)
            else -> super.energyUnitRatio(id)
        }
    }
}
