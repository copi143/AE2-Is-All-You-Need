package allyouneed.client.integration.emi.fold

import dev.emi.emi.screen.EmiScreenManager
import net.minecraft.client.gui.GuiGraphics

/**
 * 侧边栏折叠组外轮廓描边：在 `ScreenSpace.render` 收尾调用。
 *
 * 按当前页实际渲染的网格收集组槽，同组相邻格共享边不画（见 [EmiFoldEdges]），
 * 只描合并外圈。展开块连成一片时内部无分隔线，一眼可辨同组范围。
 * 仅应从 Mixin 调用（EMI 存在时）。
 */
object EmiFoldOutline {
    @JvmStatic
    fun drawOutlines(
        space: EmiScreenManager.ScreenSpace,
        startIndex: Int,
        draw: GuiGraphics,
    ) {
        val stacks = runCatching { space.stacks }.getOrNull() ?: return
        if (stacks.isEmpty() || startIndex >= stacks.size) return

        // 与 ScreenSpace.render 完全相同的走格，定位当前页的组槽
        val cells = LinkedHashMap<Pair<Int, Int>, String>()
        var i = startIndex
        outer@ for (yo in 0 until space.th) {
            for (xo in 0 until space.getWidth(yo)) {
                if (i >= stacks.size) break@outer
                val ingredient = stacks[i++]
                val key = (ingredient as? FoldedGroupIngredient)?.groupKey
                    ?: (ingredient as? GroupedIngredient)?.groupKey
                    ?: continue
                cells[xo to yo] = key
            }
        }
        if (cells.isEmpty()) return

        val edges = EmiFoldEdges.edgesFor(cells)
        draw.pose().pushPose()
        // 与角标同层：高于图标，又低于 tooltip
        draw.pose().translate(0f, 0f, 200f)
        for ((pos, edgeSet) in edges) {
            if (edgeSet.isEmpty()) continue
            val (xo, yo) = pos
            val key = cells.getValue(pos)
            val argb = EmiFoldPalette.colorFor(key)
            val solid = (argb and 0x00FFFFFF) or 0xFF000000.toInt()
            val cx = space.getX(xo, yo)
            val cy = space.getY(xo, yo)
            if (EmiFoldEdges.Edge.TOP in edgeSet) draw.fill(cx, cy, cx + 18, cy + 1, solid)
            if (EmiFoldEdges.Edge.BOTTOM in edgeSet) draw.fill(cx, cy + 17, cx + 18, cy + 18, solid)
            if (EmiFoldEdges.Edge.LEFT in edgeSet) draw.fill(cx, cy, cx + 1, cy + 18, solid)
            if (EmiFoldEdges.Edge.RIGHT in edgeSet) draw.fill(cx + 17, cy, cx + 18, cy + 18, solid)
        }
        draw.pose().popPose()
    }
}
