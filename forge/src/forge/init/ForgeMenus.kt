package allyouneed.forge.init

import allyouneed.Platform
import allyouneed.multiblock.async.AsyncCraftingStatusMenu
import allyouneed.gtceu.multiblock.AsyncStructureGtStatusMenu
import allyouneed.parts.iodrive.MEIODriveMenu
import allyouneed.parts.logger.NetworkLoggerMenu
import allyouneed.parts.machineassembler.MachineAssemblerMenu
import allyouneed.pattern.pseudo.WirelessPseudoPatternTerminalMenu
import allyouneed.pattern.term.UnifiedPatternEncodingTermMenu
import allyouneed.util.MODID
import net.minecraft.world.inventory.MenuType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object ForgeMenus {
    val MENUS: DeferredRegister<MenuType<*>> = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID)

    val WIRELESS_PSEUDO_PATTERN_TERMINAL: RegistryObject<MenuType<WirelessPseudoPatternTerminalMenu>> =
        MENUS.register("wireless_pseudo_pattern_terminal") { WirelessPseudoPatternTerminalMenu.TYPE }

    val MACHINE_ASSEMBLER: RegistryObject<MenuType<MachineAssemblerMenu>> =
        MENUS.register("machine_assembler") { MachineAssemblerMenu.TYPE }

    val PATTERN_ENCODING_TERMINAL: RegistryObject<MenuType<UnifiedPatternEncodingTermMenu>> =
        MENUS.register("pattern_encoding_terminal") { UnifiedPatternEncodingTermMenu.TYPE }

    val ME_IO_DRIVE: RegistryObject<MenuType<MEIODriveMenu>> = MENUS.register("me_io_drive") { MEIODriveMenu.TYPE }

    val NETWORK_LOGGER: RegistryObject<MenuType<NetworkLoggerMenu>> =
        MENUS.register("network_logger") { NetworkLoggerMenu.TYPE }

    val ASYNC_CRAFTING_STATUS: RegistryObject<MenuType<AsyncCraftingStatusMenu>> =
        MENUS.register("async_crafting_status") { AsyncCraftingStatusMenu.TYPE }

    // The GT menu class only loads once its TYPE is first referenced. Registering it here (at menu
    // type registry time) both forces the early load and puts the type into the Forge registry;
    // relying on AE2's InitMenuTypes.queueRegistration alone fails because that queue is flushed once
    // during AE2's mod load, before the class is first touched at runtime (first right-click).
    val ASYNC_CRAFTING_STATUS_GT: RegistryObject<MenuType<AsyncStructureGtStatusMenu>>? =
        if (Platform.isModLoaded("gtceu")) {
            MENUS.register("async_crafting_status_gt") { AsyncStructureGtStatusMenu.TYPE }
        } else {
            null
        }

    fun register(bus: IEventBus) {
        MENUS.register(bus)
    }
}
