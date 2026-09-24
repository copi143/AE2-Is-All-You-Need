package minecraftx.compose.geometry

/**
 * Pure geometry helpers shared between the Compose layout and non-composable event handlers (which
 * must re-derive the same rect in logical coordinates for hit testing).
 */
object PanelGeometry {
    fun containsHalfOpen(px: Int, py: Int, left: Float, top: Float, size: Int): Boolean {
        val x0 = left.toInt()
        val y0 = top.toInt()
        return px >= x0 && px < x0 + size && py >= y0 && py < y0 + size
    }
}
