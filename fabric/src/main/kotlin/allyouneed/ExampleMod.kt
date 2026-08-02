package allyouneed

import allyouneed.cell.CraftingStorage
import allyouneed.cell.EnergyCell
import allyouneed.cell.dimensional.DimensionalCellStore
import allyouneed.fabric.init.FabricBlocks
import allyouneed.fabric.init.FabricItems
import allyouneed.fabric.init.FabricMenus
import allyouneed.multiblock.MultiblockEditor
import allyouneed.multiblock.MultiblockPatterns
import allyouneed.util.MODID
import allyouneed.util.logger
import appeng.api.features.P2PTunnelAttunement
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

fun init() {
    logger.info("Hello Fabric world from Kotlin!")
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
        if (!cell.selfPowered) {
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, cell.blockEntityType)
        }
    }
    Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        ResourceLocation(MODID, "self_powered_energy_cell"),
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
        ResourceLocation(MODID, "crafting_storage"),
        CraftingStorage.blockEntityType,
    )

    CommonObject.init()
    CommonObject.commonSetup()

    P2PTunnelAttunement.registerAttunementTag(FabricItems.ENTITY_P2P_TUNNEL)

    CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
        MultiblockEditor.registerCommands(dispatcher)
    }

    ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
        object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId(): ResourceLocation =
                ResourceLocation(MODID, "multiblock_patterns")

            override fun onResourceManagerReload(manager: ResourceManager) {
                MultiblockPatterns.reload(manager)
            }
        },
    )

    ServerTickEvents.END_SERVER_TICK.register { server ->
        MultiblockEditor.tick(server)
    }

    ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
        MultiblockEditor.clearSession(handler.player)
    }

    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        DimensionalCellStore.attach(server)
        MultiblockEditor.onServerStarting(server)
    }
    ServerLifecycleEvents.SERVER_STOPPING.register {
        DimensionalCellStore.detach()
    }
    ServerLifecycleEvents.SERVER_STOPPED.register { server ->
        MultiblockEditor.onServerStopped(server)
    }

    ResourceManagerHelper.registerBuiltinResourcePack(
        ResourceLocation(MODID, "editor"),
        "ae2isallyouneed_editor",
        FabricLoader.getInstance().getModContainer(MODID).get(),
        true,
    )
}
