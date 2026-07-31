package allyouneed

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
import appeng.client.gui.style.StyleManager
import appeng.client.render.SimpleModelLoader
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry
import net.minecraft.client.gui.screens.MenuScreens

fun initClient() {
    for (storage in CraftingStorage.entries) {
        val id = CraftingStorageModels.formedModelId(storage)
        ModelLoadingRegistry.INSTANCE.registerResourceProvider { _ ->
            SimpleModelLoader(id) { CraftingStorageModels.createFormedModel(storage) }
        }
    }

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
