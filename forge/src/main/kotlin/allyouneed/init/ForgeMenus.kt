package allyouneed.forge.init

import allyouneed.Constants
import allyouneed.terminal.PseudoPatternTerminalMenu
import allyouneed.terminal.WirelessPseudoPatternTerminalMenu
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ForgeMenus {
    val MENUS: DeferredRegister<MenuType<*>> = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Constants.MOD_ID)

    val PSEUDO_PATTERN_TERMINAL: RegistryObject<MenuType<PseudoPatternTerminalMenu>> =
        MENUS.register("pseudo_pattern_terminal") { PseudoPatternTerminalMenu.TYPE }

    val WIRELESS_PSEUDO_PATTERN_TERMINAL: RegistryObject<MenuType<WirelessPseudoPatternTerminalMenu>> =
        MENUS.register("wireless_pseudo_pattern_terminal") { WirelessPseudoPatternTerminalMenu.TYPE }

    fun register(bus: IEventBus) {
        MENUS.register(bus)
    }
}
