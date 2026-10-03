package allyouneed.client.integration.emi.fold

import net.minecraft.client.gui.GuiGraphics

/**
 * 折叠组槽位底色：低透明填充。
 *
 * 边框不在此画——相邻同组格要合并外轮廓（共享边不画），由
 * [EmiFoldOutline][allyouneed.client.integration.emi.fold.EmiFoldOutline]
 * 在侧边栏渲染收尾统一描边。调用方须在成员图标**之前**、同 z 调用。
 * 仅应从 EMI 存在时才会被加载的入口调用。
 */
object EmiFoldRender {
    /**
     * 按组键取色并绘制 18x18 槽位底色。
     *
     * 注意 EMI 传给 ingredient 的 x/y 是槽内图标原点（槽原点 +1），背景必须
     * 回退 1px 对齐槽位，否则整体偏右下。
     */
    fun drawGroupFill(draw: GuiGraphics, x: Int, y: Int, groupKey: String) {
        val argb = EmiFoldPalette.colorFor(groupKey)
        val sx = x - 1
        val sy = y - 1
        val rgb = argb and 0x00FFFFFF
        draw.fill(sx, sy, sx + 18, sy + 18, rgb or 0x40000000)
    }
}
