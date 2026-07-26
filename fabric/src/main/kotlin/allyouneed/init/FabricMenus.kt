package allyouneed.fabric.init

import allyouneed.iodrive.MEIODriveMenu
import allyouneed.rl
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import net.minecraft.world.inventory.MenuType

object FabricMenus {
    fun register() {
        val pseudoId = "pseudo_pattern_terminal".rl
        val wirelessId = "wireless_pseudo_pattern_terminal".rl

        @Suppress("UNUSED_VARIABLE")
        val _p: MenuType<*> = PseudoPatternTerminalMenu.TYPE
        @Suppress("UNUSED_VARIABLE")
        val _w: MenuType<*> = WirelessPseudoPatternTerminalMenu.TYPE
        @Suppress("UNUSED_VARIABLE")
        val _io: MenuType<*> = MEIODriveMenu.TYPE
    }
}
