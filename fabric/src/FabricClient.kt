package allyouneed

import allyouneed.client.guide.IayGuide
import allyouneed.multiblock.async.AsyncBlockKind
import allyouneed.multiblock.async.AsyncBlockRegistry
import allyouneed.multiblock.async.AsyncCraftingStatusMenu
import allyouneed.multiblock.async.AsyncCraftingStatusScreen
import allyouneed.cell.CraftingStorage
import allyouneed.cell.storage.AllStorageCells
import allyouneed.cell.storage.StorageCellItem
import minecraftx.compose.itemdetail.ItemDetailsKeyBind
import allyouneed.client.CraftingStorageModels
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
import allyouneed.fabric.init.FabricItems
import allyouneed.util.notify.DesktopNotify
import allyouneed.util.MODID
import allyouneed.util.logger
import appeng.api.features.P2PTunnelAttunement
import appeng.client.gui.style.StyleManager
import appeng.client.render.SimpleModelLoader
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

fun initClient() {
    logger.info("Initializing Client...")
    // 与 Forge FMLCommonSetupEvent 对齐，AE2 已完成 AEConfig/注册表初始化后执行，保证单次成功（由 AppEngClient 初始化后触发）
    Main.commonSetup()
    P2PTunnelAttunement.registerAttunementTag(FabricItems.ENTITY_P2P_TUNNEL)
    try {
        IayGuide.init()
    } catch (e: Throwable) {
        logger.warn("IayGuide init failed, skipping guide", e)
    }
    DesktopNotify.focusProbe = DesktopNotify.FocusProbe {
        Minecraft.getInstance().isWindowActive
    }
    ItemDetailsKeyBind.init()
    ColorProviderRegistry.ITEM.register(
        { stack, tintIndex -> StorageCellItem.getColor(stack, tintIndex) },
        *AllStorageCells.entries.map { it.define.asItem() }.toTypedArray(),
    )
    for (storage in CraftingStorage.entries) {
        val id = CraftingStorageModels.formedModelId(storage)
        ModelLoadingRegistry.INSTANCE.registerResourceProvider { _ ->
            SimpleModelLoader(id) { CraftingStorageModels.createFormedModel(storage) }
        }
        // Same as AE2 crafting storage: cutout so light_base alpha is not solid black
        BlockRenderLayerMap.INSTANCE.putBlock(storage.define.block(), RenderType.cutout())
    }
    // Force atlas stitch of light overlays (built-in formed models skip JSON deps)
    ModelLoadingRegistry.INSTANCE.registerModelProvider { _, out ->
        out.accept(ResourceLocation(MODID, "block/crafting/atlas_materials"))
    }

    AsyncBlockRegistry.get(AsyncBlockKind.GLASS)?.let {
        BlockRenderLayerMap.INSTANCE.putBlock(it, RenderType.cutout())
    }

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
}
