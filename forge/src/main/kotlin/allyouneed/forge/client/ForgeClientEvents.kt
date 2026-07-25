package allyouneed.forge.client

import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalScreen
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalScreen
import allyouneed.util.MODID
import appeng.client.gui.style.StyleManager
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ForgeClientEvents {
    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            MenuScreens.register(PseudoPatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")
                PseudoPatternTerminalScreen(menu, inv, title, style)
            }
            MenuScreens.register(WirelessPseudoPatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")
                WirelessPseudoPatternTerminalScreen(menu, inv, title, style)
            }
        }
    }
}
