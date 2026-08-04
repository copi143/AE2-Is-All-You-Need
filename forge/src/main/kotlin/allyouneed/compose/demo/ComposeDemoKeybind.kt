package allyouneed.compose.demo

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = "ae2isallyouneed", bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object ComposeDemoKeybind {
    val OPEN_DEMO = KeyMapping(
        "key.ae2isallyouneed.open_demo",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_K,
        "key.categories.misc"
    )

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END && OPEN_DEMO.consumeClick()) {
            Minecraft.getInstance().setScreen(ComposeDemoScreen())
        }
    }
}
