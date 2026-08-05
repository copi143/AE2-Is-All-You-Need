package allyouneed.forge.client

import allyouneed.async.AsyncBlockKind
import allyouneed.async.AsyncBlockRegistry
import allyouneed.async.AsyncCraftingStatusMenu
import allyouneed.async.AsyncCraftingStatusScreen
import allyouneed.cell.CraftingStorage
import allyouneed.cell.item.ItemStorageCell
import allyouneed.cell.item.ItemStorageCellItem
import allyouneed.client.CraftingStorageModels
import allyouneed.gt.AsyncStructureGtStatusMenu
import allyouneed.iodrive.MEIODriveMenu
import allyouneed.iodrive.MEIODriveScreen
import allyouneed.parts.machineassembler.MachineAssemblerMenu
import allyouneed.parts.machineassembler.MachineAssemblerScreen
import allyouneed.pattern.adaptive.AdaptivePatternTerminalMenu
import allyouneed.pattern.adaptive.AdaptivePatternTerminalScreen
import allyouneed.pattern.machine.MachinePatternTerminalMenu
import allyouneed.pattern.machine.MachinePatternTerminalScreen
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalScreen
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalScreen
import allyouneed.util.MODID
import allyouneed.util.Services
import appeng.client.gui.style.StyleManager
import appeng.hooks.BuiltInModelHooks
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ModelEvent
import net.minecraftforge.client.event.RegisterColorHandlersEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object ForgeClientEvents {
    init {
        // Must run before model baking (same timing as AdvancedAE).
        for (storage in CraftingStorage.entries) {
            BuiltInModelHooks.addBuiltInModel(
                CraftingStorageModels.formedModelId(storage),
                CraftingStorageModels.createFormedModel(storage),
            )
        }
    }

    @SubscribeEvent
    fun onRegisterItemColors(event: RegisterColorHandlersEvent.Item) {
        event.register(
            { stack, tintIndex -> ItemStorageCellItem.getColor(stack, tintIndex) },
            *ItemStorageCell.entries.map { it.item.asItem() }.toTypedArray(),
        )
    }

    /** Pull light overlays into the blocks atlas (built-in formed models skip JSON deps). */
    @SubscribeEvent
    fun onRegisterAdditionalModels(event: ModelEvent.RegisterAdditional) {
        event.register(ResourceLocation(MODID, "block/crafting/atlas_materials"))
    }

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            // AE2 crafting storage uses cutout so light_base transparency is not solid black
            for (storage in CraftingStorage.entries) {
                ItemBlockRenderTypes.setRenderLayer(storage.define.block(), RenderType.cutout())
            }

            ItemBlockRenderTypes.setRenderLayer(
                AsyncBlockRegistry.get(AsyncBlockKind.GLASS),
                RenderType.cutout(),
            )

            MenuScreens.register(PseudoPatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")
                PseudoPatternTerminalScreen(menu, inv, title, style)
            }
            MenuScreens.register(WirelessPseudoPatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")
                WirelessPseudoPatternTerminalScreen(menu, inv, title, style)
            }
            MenuScreens.register(MEIODriveMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/drive.json")
                MEIODriveScreen(menu, inv, title, style)
            }
            MenuScreens.register(AdaptivePatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/adaptive_pattern_encoding_terminal.json")
                AdaptivePatternTerminalScreen(menu, inv, title, style)
            }
            MenuScreens.register(MachineAssemblerMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/machine_assembler.json")
                MachineAssemblerScreen(menu, inv, title, style)
            }
            MenuScreens.register(MachinePatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/machine_pattern_encoding_terminal.json")
                MachinePatternTerminalScreen(menu, inv, title, style)
            }
            MenuScreens.register(AsyncCraftingStatusMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/async_crafting_status.json")
                AsyncCraftingStatusScreen(menu, inv, title, style)
            }
            if (Services.platform.isModLoaded("gtceu")) {
                MenuScreens.register(AsyncStructureGtStatusMenu.TYPE) { menu, inv, title ->
                    val style = StyleManager.loadStyleDoc("/screens/async_crafting_status.json")
                    AsyncCraftingStatusScreen(menu, inv, title, style)
                }
            }
        }
    }
}
