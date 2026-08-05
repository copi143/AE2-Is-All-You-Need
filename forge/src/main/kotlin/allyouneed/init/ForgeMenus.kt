package allyouneed.forge.init

import allyouneed.async.AsyncCraftingStatusMenu
import allyouneed.gt.AsyncStructureGtStatusMenu
import allyouneed.iodrive.MEIODriveMenu
import allyouneed.parts.machineassembler.MachineAssemblerMenu
import allyouneed.pattern.adaptive.AdaptivePatternTerminalMenu
import allyouneed.pattern.machine.MachinePatternTerminalMenu
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.util.MODID
import allyouneed.util.Services
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

    val ADAPTIVE_PATTERN_TERMINAL: RegistryObject<MenuType<AdaptivePatternTerminalMenu>> =
        MENUS.register("adaptive_pattern_terminal") { AdaptivePatternTerminalMenu.TYPE }

    val MACHINE_ASSEMBLER: RegistryObject<MenuType<MachineAssemblerMenu>> =
        MENUS.register("machine_assembler") { MachineAssemblerMenu.TYPE }

    val MACHINE_PATTERN_TERMINAL: RegistryObject<MenuType<MachinePatternTerminalMenu>> =
        MENUS.register("machine_pattern_terminal") { MachinePatternTerminalMenu.TYPE }

    val ME_IO_DRIVE: RegistryObject<MenuType<MEIODriveMenu>> =
        MENUS.register("me_io_drive") { MEIODriveMenu.TYPE }

    val ASYNC_CRAFTING_STATUS: RegistryObject<MenuType<AsyncCraftingStatusMenu>> =
        MENUS.register("async_crafting_status") { AsyncCraftingStatusMenu.TYPE }

    // The GT menu class only loads once its TYPE is first referenced. Registering it here (at menu
    // type registry time) both forces the early load and puts the type into the Forge registry;
    // relying on AE2's InitMenuTypes.queueRegistration alone fails because that queue is flushed once
    // during AE2's mod load, before the class is first touched at runtime (first right-click).
    val ASYNC_CRAFTING_STATUS_GT: RegistryObject<MenuType<AsyncStructureGtStatusMenu>>? =
        if (Services.platform.isModLoaded("gtceu")) {
            MENUS.register("async_crafting_status_gt") { AsyncStructureGtStatusMenu.TYPE }
        } else {
            null
        }

    fun register(bus: IEventBus) {
        MENUS.register(bus)
    }
}
