package allyouneed.fabric

import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalScreen
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalScreen
import appeng.client.gui.style.StyleManager
import net.minecraft.client.gui.screens.MenuScreens

fun initClient() {
    MenuScreens.register(PseudoPatternTerminalMenu.TYPE) { menu, inv, title ->
        val style = StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")
        PseudoPatternTerminalScreen(menu, inv, title, style)
    }
    MenuScreens.register(WirelessPseudoPatternTerminalMenu.TYPE) { menu, inv, title ->
        val style = StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")
        WirelessPseudoPatternTerminalScreen(menu, inv, title, style)
    }
}
