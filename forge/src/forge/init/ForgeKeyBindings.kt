package allyouneed.forge.init

import minecraftx.compose.itemdetail.ItemDetailsKeyBind
import allyouneed.util.MODID
import net.minecraft.client.KeyMapping
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/**
 * Forge-side wiring for key bindings registered through [allyouneed.util.interfaces.PlatformHelper].
 *
 * The common [ItemDetailsKeyBind] is initialised inside the `RegisterKeyMappingsEvent` handler so
 * that its `KeyMapping` is guaranteed to be registered in the game before the event fires.
 */
internal object ForgeKeyBindings {
    val keys = mutableListOf<KeyMapping>()
    val tickHandlers = mutableListOf<() -> Unit>()
}

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ForgeKeyBindingRegistration {
    @SubscribeEvent
    fun onRegisterKeys(event: RegisterKeyMappingsEvent) {
        ItemDetailsKeyBind.init()
        ForgeKeyBindings.keys.forEach(event::register)
    }
}

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object ForgeKeyBindingTick {
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            ForgeKeyBindings.tickHandlers.toList().forEach { it() }
        }
    }
}
