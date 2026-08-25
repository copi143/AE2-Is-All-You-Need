package allyouneed

import allyouneed.fabric.init.FabricItems
import appeng.api.features.P2PTunnelAttunement
import net.fabricmc.api.DedicatedServerModInitializer

class FabricServer : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        // 与 Forge 的 FMLCommonSetupEvent 对齐，在 AE2 完成 AEConfig/注册表初始化后执行，保证单次成功
        // registerAEKeyTypes 已由 Mixin: InitKeyTypes 完成，此处仅需 commonSetup
        Main.commonSetup()
        P2PTunnelAttunement.registerAttunementTag(FabricItems.ENTITY_P2P_TUNNEL)
    }
}
