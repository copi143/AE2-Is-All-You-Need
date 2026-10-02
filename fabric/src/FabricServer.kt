package allyouneed

import net.fabricmc.api.DedicatedServerModInitializer

class FabricServer : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        // 与 Forge 的 FMLCommonSetupEvent 对齐：AEConfig 就绪（AE2 入口先跑）则直接执行，
        // 否则由 AppEngServerStartupMixin 在 AE2 初始化完成后触发，两种顺序都恰好一次
        FabricAE2Hooks.afterAE2InitIfReady()
    }
}
