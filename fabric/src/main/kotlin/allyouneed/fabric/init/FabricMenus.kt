package allyouneed.fabric.init

import allyouneed.async.AsyncCraftingStatusMenu
import allyouneed.iodrive.MEIODriveMenu
import allyouneed.parts.machineassembler.MachineAssemblerMenu
import allyouneed.pattern.adaptive.AdaptivePatternTerminalMenu
import allyouneed.pattern.machine.MachinePatternTerminalMenu
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.util.rl
import net.minecraft.world.inventory.MenuType

object FabricMenus {
    fun register() {
        val pseudoId = "pseudo_pattern_terminal".rl
        val wirelessId = "wireless_pseudo_pattern_terminal".rl
        val adaptiveId = "adaptive_pattern_terminal".rl

        @Suppress("UNUSED_VARIABLE") val _p: MenuType<*> = PseudoPatternTerminalMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _w: MenuType<*> = WirelessPseudoPatternTerminalMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _a: MenuType<*> = AdaptivePatternTerminalMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _ma: MenuType<*> = MachineAssemblerMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _mt: MenuType<*> = MachinePatternTerminalMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _io: MenuType<*> = MEIODriveMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _ac: MenuType<*> = AsyncCraftingStatusMenu.TYPE
    }
}
