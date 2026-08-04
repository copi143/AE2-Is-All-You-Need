package allyouneed.compose.demo

import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = "ae2isallyouneed", bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ComposeDemoKeyRegistration {
    @SubscribeEvent
    fun onRegisterKeys(event: RegisterKeyMappingsEvent) {
        event.register(ComposeDemoKeybind.OPEN_DEMO)
    }
}
