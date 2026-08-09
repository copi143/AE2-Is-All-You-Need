package allyouneed.compose.demo

import allyouneed.client.compose.demo.ComposeDemoScreen
import allyouneed.client.compose.demo.EmbeddedComposeDemoScreen
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = "ae2isallyouneed", bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object ComposeDemoKeybind {
    val OPEN_DEMO = KeyMapping(
        "key.ae2isallyouneed.open_demo",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_K,
        "key.categories.misc"
    )
    val OPEN_EMBEDDED = KeyMapping(
        "key.ae2isallyouneed.open_embedded",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_L,
        "key.categories.misc"
    )

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            if (OPEN_DEMO.consumeClick()) {
                // ComposeDemoScreen 是容器屏,需要玩家物品栏;不在世界中(主菜单)不打开。
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().setScreen(ComposeDemoScreen())
                }
            } else if (OPEN_EMBEDDED.consumeClick()) {
                Minecraft.getInstance().setScreen(EmbeddedComposeDemoScreen())
            }
        }
    }
}
