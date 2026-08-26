package allyouneed

import allyouneed.cell.CraftingStorage
import allyouneed.cell.EnergyCell
import allyouneed.cell.dimensional.DimensionalCellStore
import allyouneed.fabric.init.FabricBlocks
import allyouneed.fabric.init.FabricItems
import allyouneed.fabric.init.FabricMenus
import allyouneed.logic.machine.MachineTypeReloadListener
import allyouneed.logic.machine.ManualMachineRecipeReloadListener
import allyouneed.parts.logger.LogStore
import allyouneed.util.MODID
import allyouneed.util.id.mac.MacAddressRegistry
import allyouneed.util.logger
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

    AllRegistries.needRegisterBlockEntity.forEach { it.registerBlockEntity() }

    EnergyCell.entries.forEach { cell ->
        val block = cell.define.block()
        Registry.register(BuiltInRegistries.BLOCK, cell.blockId, block)
        Registry.register(BuiltInRegistries.ITEM, cell.blockId, cell.define.asItem())
    }
    EnergyCell.registry.forEach { (name, type) -> Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, name, type) }

    CraftingStorage.entries.forEach { storage ->
        val block = storage.define.block()
        val id = ResourceLocation(MODID, storage.blockId.path)
        Registry.register(BuiltInRegistries.BLOCK, id, block)
        Registry.register(BuiltInRegistries.ITEM, id, storage.define.asItem())
    }
    Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        "crafting_storage",
        CraftingStorage.blockEntityType,
    )

    Main.init()
    // Main.registerAEKeyTypes / Main.commonSetup 与 Forge 的 RegisterEvent / FMLCommonSetupEvent 对齐，
    // 由 Mixin: InitKeyTypesMixin 在 appeng.init.client.InitKeyTypes.init() TAIL 统一触发，保证 AEConfig 已就绪

    registerServerDataReloadListener("machine_types", MachineTypeReloadListener())
    registerServerDataReloadListener("machine_recipes", ManualMachineRecipeReloadListener())

    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        DimensionalCellStore.attach(server)
        MacAddressRegistry.attach(server)
        LogStore.attach(server)
    }
    ServerLifecycleEvents.SERVER_STOPPING.register {
        LogStore.detach()
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
