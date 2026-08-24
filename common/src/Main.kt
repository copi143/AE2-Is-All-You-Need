package allyouneed

import allyouneed.cell.creative.CreativeMeCellHandler
import allyouneed.cell.dimensional.DimensionalCellHandler
import allyouneed.cell.item.ItemStorageCell
import allyouneed.cell.item.ItemStorageCellHandler
import allyouneed.cell.mana.ManaStorageCell
import allyouneed.cell.mana.ManaStorageCellHandler
import allyouneed.logic.aekey.AllKeys
import allyouneed.logic.machine.BuiltinMachineTypes
import allyouneed.net.http.HttpModule
import allyouneed.parts.logger.NetworkLogHooks
import allyouneed.parts.p2p.EntityP2PTunnelPart
import allyouneed.pattern.ModItems
import allyouneed.pattern.ModPatternDecoders
import allyouneed.pattern.term.UnifiedPatternEncodingTermPart
import allyouneed.util.*
import appeng.api.client.StorageCellModels
import appeng.api.parts.PartModels
import appeng.api.stacks.AEKeyTypes
import appeng.api.storage.StorageCells
import appeng.core.localization.GuiText
import appeng.items.parts.PartModelsHelper

object Main {
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
        NetworkLogHooks.register()
        registerParts()
    }

    /**
     * Registers the custom AEKeyTypes. Must run before AE2's keytype registry freezes: on Forge
     * during the ae2:keytypes RegisterEvent (FMLCommonSetupEvent is already too late), on Fabric
     * during the mod initializer.
     */
    fun registerAEKeyTypes() {
        AllKeys.entries.forEach { AEKeyTypes.register(it.type) }
    }

    /**
     * Must run during common setup (after AE2 init). Registers cell handlers etc.
     */
    fun commonSetup() {
        StorageCells.addCellHandler(CreativeMeCellHandler)
        StorageCells.addCellHandler(DimensionalCellHandler)
        StorageCells.addCellHandler(ItemStorageCellHandler)
        StorageCells.addCellHandler(ManaStorageCellHandler)
        StorageCellModels.registerModel(ModItems.CREATIVE_ME_CELL, "block/drive/cells/creative_cell".rlAE)
        StorageCellModels.registerModel(ModItems.DIMENSIONAL_CELL, "block/drive/cells/creative_cell".rlAE)
        for (cell in ItemStorageCell.entries) {
            StorageCellModels.registerModel(cell.define, cell.driveCellId.joinParent("block/drive/cells"))
        }
        for (cell in ManaStorageCell.entries) {
            StorageCellModels.registerModel(cell.define, cell.driveCellId.joinParent("block/drive/cells"))
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
                ExtRef.equalDistributionCard to 1,
                ExtRef.voidCard to 1,
            )
        }
        for (cell in ManaStorageCell.entries) {
            cell.define.asItem().registerSupportedUpgrade(cellGroup).with(
                ExtRef.inverterCard to 1,
                ExtRef.fuzzyCard to 1,
                ExtRef.equalDistributionCard to 1,
                ExtRef.voidCard to 1,
            )
        }
    }

    fun registerParts() {
        PartModels.registerModels(PartModelsHelper.createModels(EntityP2PTunnelPart::class.java))
        // 专用线缆是真线缆部件（CablePart），没有静态面板模型需要注册。
        PartModels.registerModels(PartModelsHelper.createModels(UnifiedPatternEncodingTermPart::class.java))
        ModItems.PATTERN_ENCODING_TERMINAL_DEF
        ModItems.PLANE_BUS_DEF
    }
}
