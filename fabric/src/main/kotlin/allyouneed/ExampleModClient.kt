package allyouneed

import allyouneed.cell.CraftingStorage
import allyouneed.cell.ItemStorageCell
import allyouneed.cell.ItemStorageCellItem
import allyouneed.client.CraftingStorageModels
import allyouneed.iodrive.MEIODriveMenu
import allyouneed.iodrive.MEIODriveScreen
import allyouneed.pattern.adaptive.AdaptivePatternTerminalMenu
import allyouneed.pattern.adaptive.AdaptivePatternTerminalScreen
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.PseudoPatternTerminalScreen
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalMenu
import allyouneed.terminal.pseudopattern.WirelessPseudoPatternTerminalScreen
import allyouneed.util.MODID
import appeng.client.gui.style.StyleManager
import appeng.client.render.SimpleModelLoader
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

fun initClient() {
    ColorProviderRegistry.ITEM.register(
        { stack, tintIndex -> ItemStorageCellItem.getColor(stack, tintIndex) },
        *ItemStorageCell.entries.map { it.item.asItem() }.toTypedArray(),
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
}
