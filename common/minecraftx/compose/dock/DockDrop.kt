package minecraftx.compose.dock

data class DockLeafLayout(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val tabCount: Int,
    val tabBarHeight: Float,
)

fun hitDockDrop(layouts: List<DockLeafLayout>, x: Float, y: Float, edgeFraction: Float = 0.25f): DockDrop? {
    val leaf = layouts.firstOrNull { layout ->
        x >= layout.x && x <= layout.x + layout.width && y >= layout.y && y <= layout.y + layout.height
    } ?: return null
    if (leaf.width <= 0f || leaf.height <= 0f) return DockDrop.Center(leaf.id)
    val lx = x - leaf.x
    val ly = y - leaf.y
    if (leaf.tabBarHeight > 0f && ly < leaf.tabBarHeight) {
        val slots = leaf.tabCount + 1
        val index = ((lx / leaf.width) * slots).toInt().coerceIn(0, leaf.tabCount)
        return DockDrop.TabBar(leaf.id, index)
    }
    val left = lx / leaf.width
    val right = 1f - left
    val top = ly / leaf.height
    val bottom = 1f - top
    val nearest = minOf(left, right, top, bottom)
    if (nearest >= edgeFraction) return DockDrop.Center(leaf.id)
    return when (nearest) {
        left -> DockDrop.Edge(leaf.id, DockSide.Left)
        right -> DockDrop.Edge(leaf.id, DockSide.Right)
        top -> DockDrop.Edge(leaf.id, DockSide.Top)
        else -> DockDrop.Edge(leaf.id, DockSide.Bottom)
    }
}

fun dropHint(drop: DockDrop): String = when (drop) {
    is DockDrop.TabBar -> "插入标签"
    is DockDrop.Center -> "并入此组"
    is DockDrop.Edge -> when (drop.side) {
        DockSide.Left -> "拆到左侧"
        DockSide.Right -> "拆到右侧"
        DockSide.Top -> "拆到上方"
        DockSide.Bottom -> "拆到下方"
    }
}

fun dropHighlight(layout: DockLeafLayout, drop: DockDrop): DockRect? {
    if (drop is DockDrop.TabBar && drop.leafId == layout.id) {
        val slots = (layout.tabCount + 1).coerceAtLeast(1)
        val x = layout.x + layout.width * (drop.index / slots.toFloat()) - 1.5f
        return DockRect(x, layout.y, 3f, layout.tabBarHeight.coerceAtLeast(3f))
    }
    if (drop is DockDrop.Center && drop.leafId == layout.id) {
        val insetX = layout.width * 0.25f
        val insetY = layout.height * 0.25f
        return DockRect(layout.x + insetX, layout.y + insetY, layout.width - insetX * 2, layout.height - insetY * 2)
    }
    if (drop is DockDrop.Edge && drop.leafId == layout.id) {
        val halfW = layout.width * 0.5f
        val halfH = layout.height * 0.5f
        return when (drop.side) {
            DockSide.Left -> DockRect(layout.x, layout.y, halfW, layout.height)
            DockSide.Right -> DockRect(layout.x + halfW, layout.y, halfW, layout.height)
            DockSide.Top -> DockRect(layout.x, layout.y, layout.width, halfH)
            DockSide.Bottom -> DockRect(layout.x, layout.y + halfH, layout.width, halfH)
        }
    }
    return null
}

data class DockRect(val x: Float, val y: Float, val width: Float, val height: Float)
