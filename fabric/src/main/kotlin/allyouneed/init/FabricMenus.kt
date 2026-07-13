package allyouneed.fabric.init

import allyouneed.Constants
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.MenuType

object FabricMenus {
    fun register() {
        // The MenuType instances are already built in the common Menu classes via MenuTypeBuilder.
        // We just ensure they are known; MenuTypeBuilder already calls InitMenuTypes.queueRegistration.
        // On Fabric we additionally register the MenuType if needed (AE2's MenuTypeBuilder handles it via its own init).
        // To be safe we explicitly register our types by referencing them.
        val pseudoId = ResourceLocation(Constants.MOD_ID, "pseudo_pattern_terminal")
        val wirelessId = ResourceLocation(Constants.MOD_ID, "wireless_pseudo_pattern_terminal")

        // AE2 MenuTypeBuilder already registers via InitMenuTypes.queueRegistration which is loader-aware.
        // Touch the static fields to ensure initialization order.
        @Suppress("UNUSED_VARIABLE")
        val _p: MenuType<*> = PseudoPatternTerminalMenu.TYPE
        @Suppress("UNUSED_VARIABLE")
        val _w: MenuType<*> = WirelessPseudoPatternTerminalMenu.TYPE
    }
}
