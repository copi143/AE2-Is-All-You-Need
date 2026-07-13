package allyouneed

import allyouneed.fabric.init.FabricBlocks
import allyouneed.fabric.init.FabricItems
import allyouneed.fabric.init.FabricMenus

fun init() {
    Constants.LOG.info("Hello Fabric world from Kotlin!")
    FabricMenus.register()
    FabricItems.register()
    FabricBlocks.register()
    CommonObject.init()
}