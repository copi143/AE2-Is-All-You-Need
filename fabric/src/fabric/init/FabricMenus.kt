package allyouneed.fabric.init

import allyouneed.multiblock.async.AsyncCraftingStatusMenu
import allyouneed.parts.iodrive.MEIODriveMenu
import allyouneed.parts.logger.NetworkLoggerMenu
import allyouneed.parts.machineassembler.MachineAssemblerMenu
import allyouneed.pattern.pseudo.WirelessPseudoPatternTerminalMenu
import allyouneed.pattern.term.UnifiedPatternEncodingTermMenu
import allyouneed.terminal.WirelessOmniTerminalMenu
import net.minecraft.world.inventory.MenuType

@Suppress("unused", "LocalVariableName")
object FabricMenus {
    fun register() {
        val _w: MenuType<*> = WirelessPseudoPatternTerminalMenu.TYPE
        val _ma: MenuType<*> = MachineAssemblerMenu.TYPE
        val _pt: MenuType<*> = UnifiedPatternEncodingTermMenu.TYPE
        val _io: MenuType<*> = MEIODriveMenu.TYPE
        val _nl: MenuType<*> = NetworkLoggerMenu.TYPE
        val _ac: MenuType<*> = AsyncCraftingStatusMenu.TYPE
        val _wo: MenuType<*> = WirelessOmniTerminalMenu.TYPE
    }
}
