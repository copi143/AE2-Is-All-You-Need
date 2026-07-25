package allyouneed

import allyouneed.fabric.init.FabricBlocks
import allyouneed.fabric.init.FabricItems
import allyouneed.fabric.init.FabricMenus
import appeng.api.features.P2PTunnelAttunement

fun init() {
    logger.info("Hello Fabric world from Kotlin!")
    FabricMenus.register()
    FabricItems.register()
    FabricBlocks.register()
    CommonObject.init()

    P2PTunnelAttunement.registerAttunementTag(FabricItems.ENTITY_P2P_TUNNEL)
}