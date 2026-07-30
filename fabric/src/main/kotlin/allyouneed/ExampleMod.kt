package allyouneed

import allyouneed.cell.dimensional.DimensionalCellStore
import allyouneed.fabric.init.FabricBlocks
import allyouneed.fabric.init.FabricItems
import allyouneed.fabric.init.FabricMenus
import allyouneed.util.MODID
import allyouneed.util.logger
import appeng.api.features.P2PTunnelAttunement
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

fun init() {
    logger.info("Hello Fabric world from Kotlin!")
    FabricMenus.register()
    FabricItems.register()
    FabricBlocks.register()

    allyouneed.energy.EnergyCell.entries.forEach { it.registerBEType() }

    allyouneed.energy.EnergyCell.entries.forEach { cell ->
        val block = cell.define.block()
        val id = ResourceLocation(MODID, cell.blockId.path)
        Registry.register(BuiltInRegistries.BLOCK, id, block)
        Registry.register(BuiltInRegistries.ITEM, id, cell.define.asItem())
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, cell.blockEntityType)
    }

    CommonObject.init()
    CommonObject.commonSetup()

    P2PTunnelAttunement.registerAttunementTag(FabricItems.ENTITY_P2P_TUNNEL)

    ServerLifecycleEvents.SERVER_STARTING.register { server ->
        DimensionalCellStore.attach(server)
    }
    ServerLifecycleEvents.SERVER_STOPPING.register {
        DimensionalCellStore.detach()
    }
}
