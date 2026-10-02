package allyouneed

import allyouneed.fabric.init.FabricItems
import appeng.api.features.P2PTunnelAttunement
import appeng.core.AEConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fabric 上 commonSetup 的确定性时序：AE2 的 `server`/`client` 入口与我们同名入口的
 * 执行顺序不确定，先跑会撞上 AEConfig 未加载。
 *
 * 由 mixin 在 `AppEngServerStartup.onInitializeServer` / `AppEngClientStartup.onInitializeClient`
 * 的 TAIL（`new AppEngServer/AppEngClient` 完成、AEConfig 已加载之后）调用 [afterAE2Init]；
 * 我们自己的入口点只调 [afterAE2InitIfReady]（AE2 先跑则直接执行，否则等 mixin）。
 * 两条路径经 [done] 保证恰好一次。
 */
object FabricAE2Hooks {
    private val done = AtomicBoolean(false)

    @JvmStatic
    fun afterAE2Init() {
        if (!done.compareAndSet(false, true)) return
        // 与 Forge 的 FMLCommonSetupEvent 对齐：AEConfig/注册表初始化完成后执行
        CommonMain.commonSetup()
        P2PTunnelAttunement.registerAttunementTag(FabricItems.ENTITY_P2P_TUNNEL)
    }

    @JvmStatic
    fun afterAE2InitIfReady() {
        if (AEConfig.instance() != null) afterAE2Init()
    }
}
