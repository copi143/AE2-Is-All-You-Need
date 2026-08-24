package allyouneed.client

import allyouneed.Platform
import allyouneed.client.guide.IayGuide
import allyouneed.multiblock.async.AsyncBlockKind
import allyouneed.multiblock.async.AsyncBlockRegistry
import allyouneed.multiblock.async.AsyncCraftingStatusMenu
import allyouneed.multiblock.async.AsyncCraftingStatusScreen
import allyouneed.cell.CraftingStorage
import allyouneed.cell.item.ItemStorageCell
import allyouneed.cell.item.ResourceCellItem
import allyouneed.cell.mana.ManaStorageCell
import allyouneed.gtceu.multiblock.AsyncStructureGtStatusMenu
import allyouneed.parts.iodrive.MEIODriveMenu
import allyouneed.parts.iodrive.MEIODriveScreen
import allyouneed.parts.logger.NetworkLoggerMenu
import allyouneed.parts.logger.NetworkLoggerScreen
import allyouneed.parts.machineassembler.MachineAssemblerMenu
import allyouneed.parts.machineassembler.MachineAssemblerScreen
import allyouneed.pattern.pseudo.WirelessPseudoPatternTerminalMenu
import allyouneed.pattern.pseudo.WirelessPseudoPatternTerminalScreen
import allyouneed.pattern.term.UnifiedPatternEncodingTermMenu
import allyouneed.pattern.term.UnifiedPatternEncodingTermScreen
import allyouneed.util.notify.DesktopNotify
import allyouneed.util.MODID
import appeng.client.gui.style.StyleManager
import appeng.hooks.BuiltInModelHooks
import net.minecraft.client.Minecraft
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
        // Register the GuideME guidebook as early as AE2 does (mod construction).
        IayGuide.init()
    }

    @SubscribeEvent
    fun onRegisterItemColors(event: RegisterColorHandlersEvent.Item) {
        event.register(
            { stack, tintIndex -> ResourceCellItem.getColor(stack, tintIndex) },
            *ItemStorageCell.entries.map { it.define.asItem() }.toTypedArray(),
            *ManaStorageCell.entries.map { it.define.asItem() }.toTypedArray(),
        )
    }

    /** Pull light overlays into the blocks atlas (built-in formed models skip JSON deps). */
    @SubscribeEvent
    fun onRegisterAdditionalModels(event: ModelEvent.RegisterAdditional) {
        event.register(ResourceLocation(MODID, "block/crafting/atlas_materials"))
    }

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        DesktopNotify.focusProbe = DesktopNotify.FocusProbe {
            Minecraft.getInstance().isWindowActive
        }
        event.enqueueWork {
            // AE2 crafting storage uses cutout so light_base transparency is not solid black
            for (storage in CraftingStorage.entries) {
                ItemBlockRenderTypes.setRenderLayer(storage.define.block(), RenderType.cutout())
            }

            ItemBlockRenderTypes.setRenderLayer(
                AsyncBlockRegistry.get(AsyncBlockKind.GLASS),
                RenderType.cutout(),
            )

            MenuScreens.register(WirelessPseudoPatternTerminalMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/wireless_terminal.json")
                WirelessPseudoPatternTerminalScreen(menu, inv, title, style)
            }
            MenuScreens.register(MEIODriveMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/drive.json")
                MEIODriveScreen(menu, inv, title, style)
            }
            MenuScreens.register(NetworkLoggerMenu.TYPE) { menu, inv, title ->
                NetworkLoggerScreen(menu, inv, title)
            }
            MenuScreens.register(MachineAssemblerMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/machine_assembler.json")
                MachineAssemblerScreen(menu, inv, title, style)
            }
            MenuScreens.register(UnifiedPatternEncodingTermMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/terminals/allyouneed_pattern_encoding_terminal.json")
                UnifiedPatternEncodingTermScreen(menu, inv, title, style)
            }
            MenuScreens.register(AsyncCraftingStatusMenu.TYPE) { menu, inv, title ->
                val style = StyleManager.loadStyleDoc("/screens/async_crafting_status.json")
                AsyncCraftingStatusScreen(menu, inv, title, style)
            }
            if (Platform.isModLoaded("gtceu")) {
                MenuScreens.register(AsyncStructureGtStatusMenu.TYPE) { menu, inv, title ->
                    val style = StyleManager.loadStyleDoc("/screens/async_crafting_status.json")
                    AsyncCraftingStatusScreen(menu, inv, title, style)
                }
            }
        }
    }
}
