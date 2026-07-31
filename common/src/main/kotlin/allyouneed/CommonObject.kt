package allyouneed

import allyouneed.api.machine.BuiltinMachineTypes
import allyouneed.cell.CreativeMeCellHandler
import allyouneed.cell.dimensional.DimensionalCellHandler
import allyouneed.parts.p2p.EntityP2PTunnelPart
import allyouneed.pattern.ModItems
import allyouneed.pattern.ModPatternDecoders
import allyouneed.util.Services
import allyouneed.util.logger
import appeng.api.client.StorageCellModels
import appeng.api.parts.PartModels
import appeng.api.storage.StorageCells
import appeng.api.upgrades.Upgrades
import appeng.core.definitions.AEItems
import appeng.core.localization.GuiText
import appeng.items.parts.PartModelsHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Items

// This class is part of the common project meaning it is shared between all supported loaders. Code written here can only
// import and access the vanilla codebase, libraries used by vanilla, and optionally third party libraries that provide
// common compatible binaries. This means common code can not directly use loader specific concepts such as Forge events
// however it will be compatible with all supported mod loaders.
object CommonObject {
    // The loader specific projects are able to import and use any code from the common project. This allows you to
    // write the majority of your code here and load it from your loader specific projects. This example has some
    // code that gets invoked by the entry point of the loader specific projects.
    fun init() {
        logger.info(
            "Hello from Common init on {}! we are currently in a {} environment!",
            Services.platform.name,
            Services.platform.getEnvironmentName()
        )
        logger.info("The ID for diamonds is {}", BuiltInRegistries.ITEM.getKey(Items.DIAMOND))

        // It is common for all supported loaders to provide a similar feature that can not be used directly in the
        // common code. A popular way to get around this is using Java's built-in service loader feature to create
        // your own abstraction layer. You can learn more about this in our provided services class. In this example
        // we have an interface in the common code and use a loader specific implementation to delegate our call to
        // the platform specific approach.
        if (Services.platform.isModLoaded("examplemod")) {
            logger.info("Hello to examplemod")
        }

        // Register our machine types, pattern decoders, and part models
        BuiltinMachineTypes.registerAll()
        ModPatternDecoders.register()
        registerParts()
    }

    /**
     * Must run during common setup (after AE2 init). Registers cell handlers etc.
     */
    fun commonSetup() {
        StorageCells.addCellHandler(CreativeMeCellHandler)
        StorageCells.addCellHandler(DimensionalCellHandler)
        StorageCellModels.registerModel(
            ModItems.CREATIVE_ME_CELL,
            ResourceLocation("ae2", "block/drive/cells/creative_cell"),
        )
        StorageCellModels.registerModel(
            ModItems.DIMENSIONAL_CELL,
            ResourceLocation("ae2", "block/drive/cells/creative_cell"),
        )
        val cellGroup = GuiText.StorageCells.translationKey
        Upgrades.add(AEItems.INVERTER_CARD, ModItems.DIMENSIONAL_CELL, 1, cellGroup)
        Upgrades.add(AEItems.FUZZY_CARD, ModItems.DIMENSIONAL_CELL, 1, cellGroup)
    }

    fun registerParts() {
        PartModels.registerModels(PartModelsHelper.createModels(EntityP2PTunnelPart::class.java))
    }
}
