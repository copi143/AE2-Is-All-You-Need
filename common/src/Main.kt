package allyouneed

import allyouneed.cell.creative.CreativeMeCellHandler
import allyouneed.cell.dimensional.DimensionalCellHandler
import allyouneed.cell.item.ItemStorageCell
import allyouneed.cell.item.ItemStorageCellHandler
import allyouneed.logic.aekey.EnergyKey
import allyouneed.logic.aekey.ManaKey
import allyouneed.logic.aekey.VirtualKey
import allyouneed.logic.machine.BuiltinMachineTypes
import allyouneed.net.http.HttpModule
import allyouneed.parts.p2p.EntityP2PTunnelPart
import allyouneed.pattern.term.UnifiedPatternEncodingTermPart
import allyouneed.pattern.ModItems
import allyouneed.pattern.ModPatternDecoders
import allyouneed.util.*
import appeng.api.client.StorageCellModels
import appeng.api.parts.PartModels
import appeng.api.stacks.AEKeyTypes
import appeng.api.storage.StorageCells
import appeng.core.definitions.AEItems
import appeng.core.localization.GuiText
import appeng.items.parts.PartModelsHelper

object Main {
    // The loader specific projects are able to import and use any code from the common project. This allows you to
    // write the majority of your code here and load it from your loader specific projects. This example has some
    // code that gets invoked by the entry point of the loader specific projects.
    fun init() {
        debugLogger.info(
            "Hello from Common init on {}! we are currently in a {} environment!",
            Platform.name,
            Platform.envName,
        )
        // Register our pattern decoders and part models
        ModPatternDecoders.register()
        BuiltinMachineTypes.registerAll()
        HttpModule.register()
        registerParts()
    }

    /**
     * Registers the custom AEKeyTypes. Must run before AE2's keytype registry freezes: on Forge
     * during the ae2:keytypes RegisterEvent (FMLCommonSetupEvent is already too late), on Fabric
     * during the mod initializer.
     */
    fun registerAEKeyTypes() {
        AEKeyTypes.register(EnergyKey.Type)
        AEKeyTypes.register(ManaKey.Type)
        AEKeyTypes.register(VirtualKey.Type)
    }

    /**
     * Must run during common setup (after AE2 init). Registers cell handlers etc.
     */
    fun commonSetup() {
        StorageCells.addCellHandler(CreativeMeCellHandler)
        StorageCells.addCellHandler(DimensionalCellHandler)
        StorageCells.addCellHandler(ItemStorageCellHandler)
        StorageCellModels.registerModel(ModItems.CREATIVE_ME_CELL, "block/drive/cells/creative_cell".rlAE)
        StorageCellModels.registerModel(ModItems.DIMENSIONAL_CELL, "block/drive/cells/creative_cell".rlAE)
        for (cell in ItemStorageCell.entries) {
            StorageCellModels.registerModel(cell.define, "block/drive/cells/${cell.driveCellId.path}".rl)
        }
        val cellGroup = GuiText.StorageCells.translationKey
        ModItems.DIMENSIONAL_CELL.registerSupportedUpgrade(cellGroup).with(
            ExtRef.inverterCard to 1,
            ExtRef.fuzzyCard to 1,
        )
        for (cell in ItemStorageCell.entries) {
            cell.define.asItem().registerSupportedUpgrade(cellGroup).with(
                ExtRef.inverterCard to 1,
                ExtRef.fuzzyCard to 1,
                AEItems.EQUAL_DISTRIBUTION_CARD to 1,
                AEItems.VOID_CARD to 1,
            )
        }
    }

    fun registerParts() {
        PartModels.registerModels(PartModelsHelper.createModels(EntityP2PTunnelPart::class.java))
        PartModels.registerModels(PartModelsHelper.createModels(UnifiedPatternEncodingTermPart::class.java))
        ModItems.PATTERN_ENCODING_TERMINAL_DEF
    }
}
