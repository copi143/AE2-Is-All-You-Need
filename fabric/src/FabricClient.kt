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
import allyouneed.terminal.WirelessOmniTerminalMenu
import allyouneed.terminal.WirelessOmniTerminalScreen
import allyouneed.fabric.init.FabricItems
import allyouneed.util.notify.DesktopNotify
import allyouneed.util.MODID
import allyouneed.util.logger
import appeng.api.features.P2PTunnelAttunement
import appeng.client.gui.style.StyleManager
import appeng.client.render.SimpleModelLoader
import minecraftx.compose.text.McTextEngines
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

fun initClient() {
    logger.info("Initializing Client...")
    allyouneed.client.render.AEKeyRenderers.init()
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
    allyouneed.client.compose.platform.ComposePrewarm.startAsync()
    // 资源重载时释放 MSDF 文本引擎的 GPU 资源(shader/图集纹理/VAO),下次绘制时惰性重建。
    ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
        object : IdentifiableResourceReloadListener {
            override fun getFabricId(): ResourceLocation = ResourceLocation(MODID, "msdf_text_gl")

            override fun reload(
                preparationBarrier: PreparableReloadListener.PreparationBarrier,
                resourceManager: ResourceManager,
                preparationsProfiler: ProfilerFiller,
                reloadProfiler: ProfilerFiller,
                backgroundExecutor: Executor,
                gameExecutor: Executor,
            ): CompletableFuture<Void> = CompletableFuture.completedFuture<Void?>(null)
                .thenCompose { preparationBarrier.wait(null) }
                .thenRunAsync({ McTextEngines.releaseMsdfGl() }, gameExecutor)
        },
    )
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
    MenuScreens.register(WirelessOmniTerminalMenu.TYPE) { menu, inv, title ->
        WirelessOmniTerminalScreen(menu, inv, title)
    }
}
