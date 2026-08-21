package allyouneed.forge.init

import allyouneed.cell.dimensional.DimensionalCellStore
import allyouneed.parts.logger.LogStore
import allyouneed.logic.machine.MachineTypeReloadListener
import allyouneed.logic.machine.ManualMachineRecipeReloadListener
import allyouneed.util.MODID
import allyouneed.util.id.mac.MacAddressRegistry
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ForgeServerEvents {
    @SubscribeEvent
    fun onAddReloadListeners(event: AddReloadListenerEvent) {
        // 先类型后配方，保证日志时别名/归一化已就绪
        event.addListener(MachineTypeReloadListener())
        event.addListener(ManualMachineRecipeReloadListener())
    }

    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        DimensionalCellStore.attach(event.server)
        MacAddressRegistry.attach(event.server)
        LogStore.attach(event.server)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        LogStore.detach()
        MacAddressRegistry.detach()
        DimensionalCellStore.detach()
    }
}
