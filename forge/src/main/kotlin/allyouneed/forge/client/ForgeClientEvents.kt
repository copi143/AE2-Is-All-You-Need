package allyouneed.forge.client

import allyouneed.cell.CraftingStorage
import allyouneed.client.CraftingStorageModels
import allyouneed.iodrive.MEIODriveMenu
import allyouneed.iodrive.MEIODriveScreen
import allyouneed.pattern.adaptive.AdaptivePatternTerminalMenu
import allyouneed.pattern.adaptive.AdaptivePatternTerminalScreen
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalScreen
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalScreen
import allyouneed.util.MODID
import appeng.client.gui.style.StyleManager
import appeng.hooks.BuiltInModelHooks
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ForgeClientEvents {
    init {
        // Must run before model baking (same timing as AdvancedAE).
        for (storage in CraftingStorage.entries) {
            BuiltInModelHooks.addBuiltInModel(
                CraftingStorageModels.formedModelId(storage),
                CraftingStorageModels.createFormedModel(storage),
            )
        }
    }

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
            MenuScreens.register(MEIODriveMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/drive.json")
                MEIODriveScreen(menu, inv, title, style)
            }
            MenuScreens.register(AdaptivePatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/adaptive_pattern_encoding_terminal.json")
                AdaptivePatternTerminalScreen(menu, inv, title, style)
            }
        }
    }
}
