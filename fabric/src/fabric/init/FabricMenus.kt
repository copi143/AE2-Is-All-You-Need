package allyouneed.fabric.init

import allyouneed.multiblock.async.AsyncCraftingStatusMenu
import allyouneed.parts.iodrive.MEIODriveMenu
import allyouneed.parts.machineassembler.MachineAssemblerMenu
import allyouneed.pattern.pseudo.WirelessPseudoPatternTerminalMenu
import allyouneed.pattern.term.UnifiedPatternEncodingTermMenu
import net.minecraft.world.inventory.MenuType

object FabricMenus {
    fun register() {
        @Suppress("UNUSED_VARIABLE") val _w: MenuType<*> = WirelessPseudoPatternTerminalMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _ma: MenuType<*> = MachineAssemblerMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _pt: MenuType<*> = UnifiedPatternEncodingTermMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _io: MenuType<*> = MEIODriveMenu.TYPE

        @Suppress("UNUSED_VARIABLE") val _ac: MenuType<*> = AsyncCraftingStatusMenu.TYPE
    }
}
