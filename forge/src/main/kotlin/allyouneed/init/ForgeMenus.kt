package allyouneed.forge.init

import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.util.MODID
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ForgeMenus {
    val MENUS: DeferredRegister<MenuType<*>> = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID)

    val PSEUDO_PATTERN_TERMINAL: RegistryObject<MenuType<PseudoPatternTerminalMenu>> =
        MENUS.register("pseudo_pattern_terminal") { PseudoPatternTerminalMenu.TYPE }

    val WIRELESS_PSEUDO_PATTERN_TERMINAL: RegistryObject<MenuType<WirelessPseudoPatternTerminalMenu>> =
        MENUS.register("wireless_pseudo_pattern_terminal") { WirelessPseudoPatternTerminalMenu.TYPE }

    fun register(bus: IEventBus) {
        MENUS.register(bus)
    }
}
