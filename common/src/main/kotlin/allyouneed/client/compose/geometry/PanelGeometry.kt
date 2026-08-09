package allyouneed.client.compose.geometry

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/**
 * Pure geometry helpers shared between the Compose layout (which centers a fixed panel with
 * `Alignment.Center`) and non-composable event handlers (which must re-derive the same rect in
 * logical coordinates for hit testing). Keeping both derived from one math function prevents the
 * two paths from drifting.
 */
object PanelGeometry {
    /** A [panelSize] rect centered inside [viewSize] (both in logical units). */
    fun centeredRect(viewSize: IntSize, panelSize: IntSize): Rect {
        val left = (viewSize.width - panelSize.width) / 2f
        val top = (viewSize.height - panelSize.height) / 2f
        return Rect(left, top, left + panelSize.width, top + panelSize.height)
    }

    /** Shrinks [rect] by the given insets (right/bottom default to left/top). */
    fun inset(rect: Rect, left: Int, top: Int, right: Int = left, bottom: Int = top): Rect =
        Rect(
            rect.left + left,
            rect.top + top,
            rect.right - right,
            rect.bottom - bottom,
        )
}
