package allyouneed.forge.init

import allyouneed.cell.dimensional.DimensionalCellStore
import allyouneed.netaddr.mac.MacAddressRegistry
import allyouneed.util.id.KeyIdRegistry
import allyouneed.util.MODID
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ForgeServerEvents {
    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        DimensionalCellStore.attach(event.server)
        MacAddressRegistry.attach(event.server)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        MacAddressRegistry.detach()
        DimensionalCellStore.detach()
        KeyIdRegistry.clear()
    }
}
