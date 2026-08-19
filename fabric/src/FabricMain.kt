package allyouneed

import allyouneed.cell.CraftingStorage
import allyouneed.cell.EnergyCell
import allyouneed.cell.dimensional.DimensionalCellStore
import allyouneed.fabric.init.FabricBlocks
import allyouneed.fabric.init.FabricItems
import allyouneed.fabric.init.FabricMenus
import allyouneed.logic.machine.MachineTypeReloadListener
import allyouneed.logic.machine.ManualMachineRecipeReloadListener
import allyouneed.util.MODID
import allyouneed.util.id.mac.MacAddressRegistry
import allyouneed.util.logger
import allyouneed.util.rl
import appeng.api.features.P2PTunnelAttunement
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

fun init() {
    logger.info("Initializing...")

    FabricMenus.register()
    FabricItems.register()
    FabricBlocks.register()

    EnergyCell.registerSelfPoweredBEType()
    EnergyCell.entries.forEach { it.registerBEType() }
    CraftingStorage.registerBEType()

    EnergyCell.entries.forEach { cell ->
        val block = cell.define.block()
        val id = ResourceLocation(MODID, cell.blockId.path)
        Registry.register(BuiltInRegistries.BLOCK, id, block)
        Registry.register(BuiltInRegistries.ITEM, id, cell.define.asItem())
        if (!cell.isSelfPowered) {
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, cell.blockEntityType)
        }
    }
    Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        "self_powered_energy_cell".rl,
        EnergyCell.selfPoweredBlockEntityType,
    )

    CraftingStorage.entries.forEach { storage ->
        val block = storage.define.block()
        val id = ResourceLocation(MODID, storage.blockId.path)
        Registry.register(BuiltInRegistries.BLOCK, id, block)
        Registry.register(BuiltInRegistries.ITEM, id, storage.define.asItem())
    }
    Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        "crafting_storage".rl,
        CraftingStorage.blockEntityType,
    )

    Main.init()
    Main.registerAEKeyTypes()
    Main.commonSetup()

    P2PTunnelAttunement.registerAttunementTag(FabricItems.ENTITY_P2P_TUNNEL)

    registerServerDataReloadListener("machine_types", MachineTypeReloadListener())
    registerServerDataReloadListener("machine_recipes", ManualMachineRecipeReloadListener())

    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        DimensionalCellStore.attach(server)
        MacAddressRegistry.attach(server)
    }
    ServerLifecycleEvents.SERVER_STOPPING.register {
        MacAddressRegistry.detach()
        DimensionalCellStore.detach()
    }
}

private fun registerServerDataReloadListener(path: String, delegate: PreparableReloadListener) {
    ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
        object : IdentifiableResourceReloadListener {
            override fun getFabricId(): ResourceLocation = ResourceLocation(MODID, path)

            override fun reload(
                preparationBarrier: PreparableReloadListener.PreparationBarrier,
                resourceManager: ResourceManager,
                preparationsProfiler: ProfilerFiller,
                reloadProfiler: ProfilerFiller,
                backgroundExecutor: Executor,
                gameExecutor: Executor,
            ): CompletableFuture<Void> = delegate.reload(
                preparationBarrier,
                resourceManager,
                preparationsProfiler,
                reloadProfiler,
                backgroundExecutor,
                gameExecutor,
            )
        },
    )
}
