package allyouneed.client.guide

import allyouneed.util.MODID
import guideme.Guide
import net.minecraft.resources.ResourceLocation

/**
 * GuideME 指南书接入（与 AE2 的 AppEngClient#createGuide 相同模式）：资源位于
 * `assets/ae2isallyouneed/guide/` 下的 Markdown 页面，页面 id 命名空间默认为本模组，
 * 因此 md 内 `<ItemLink id="plane_bus" />` 等短 id 直接解析到本模组内容。
 *
 * GuideME book integration (same pattern as AE2's AppEngClient#createGuide): pages live under
 * `assets/ae2isallyouneed/guide/`, the default page/item namespace is this mod, so short ids
 * like `<ItemLink id="plane_bus" />` resolve against our own registry.
 *
 * [init] 必须在客户端初始化尽早调用（mod 构造 / client entrypoint），与 AE2 一致；
 * GuideME 默认注册“打开指南”热键与物品 tooltip 的按住 G 跳转，无需额外按键代码。
 *
 * [init] must run as early as possible on the client (mod construction / client entrypoint),
 * mirroring AE2. GuideME registers its open hotkey and the tooltip hold-G jump by default,
 * so no key binding code is needed here.
 */
object IayGuide {

    @JvmField
    val GUIDE_ID: ResourceLocation = ResourceLocation(MODID, "guide")

    lateinit var guide: Guide
        private set

    @JvmStatic
    fun init() {
        if (::guide.isInitialized) {
            return
        }
        guide = Guide.builder(GUIDE_ID)
            .folder("guide")
            .build()
    }
}
