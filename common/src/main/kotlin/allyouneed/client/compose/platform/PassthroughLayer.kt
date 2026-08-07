@file:Suppress(
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER",
    "EXPOSED_PARAMETER_TYPE",
    "DEPRECATION",
    "DEPRECATION_ERROR",
)

package allyouneed.client.compose.platform

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Zero-offscreen layer: instead of rendering content into a GPU/offscreen surface, the drawing
 * block is invoked directly against the [Canvas] passed to [drawLayer] (a [McCanvas] bridging to
 * Minecraft's [net.minecraft.client.gui.GuiGraphics]). Layer properties such as clipping and alpha
 * are ignored; only the layer position is honoured, so the official placement output maps 1:1 to
 * screen coordinates.
 */
class PassthroughLayer(
    private var drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
    private var invalidateParentLayer: () -> Unit,
) : OwnedLayer {
    private var position: IntOffset = IntOffset.Zero
    private var size: IntSize = IntSize.Zero

    override fun updateLayerProperties(scope: ReusableGraphicsLayerScope) {}

    override fun isInLayer(position: Offset): Boolean = true

    override fun move(position: IntOffset) {
        this.position = position
    }

    override fun resize(size: IntSize) {
        this.size = size
    }

    override fun drawLayer(canvas: Canvas, parentLayer: GraphicsLayer?) {
        if (position != IntOffset.Zero) {
            canvas.save()
            canvas.translate(position.x.toFloat(), position.y.toFloat())
            drawBlock(canvas, parentLayer)
            canvas.restore()
        } else {
            drawBlock(canvas, parentLayer)
        }
    }

    override fun updateDisplayList() {}

    override fun invalidate() {
        invalidateParentLayer()
    }

    override fun destroy() {}

    override fun mapOffset(point: Offset, inverse: Boolean): Offset =
        if (inverse) point - Offset(position.x.toFloat(), position.y.toFloat())
        else point + Offset(position.x.toFloat(), position.y.toFloat())

    override fun mapBounds(rect: MutableRect, inverse: Boolean) {
        val dx = if (inverse) -position.x else position.x
        val dy = if (inverse) -position.y else position.y
        rect.left += dx
        rect.top += dy
        rect.right += dx
        rect.bottom += dy
    }

    override fun reuseLayer(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
    ) {
        this.drawBlock = drawBlock
        this.invalidateParentLayer = invalidateParentLayer
    }

    override fun transform(matrix: Matrix) = Unit

    override val underlyingMatrix: Matrix = Matrix()

    override var frameRate: Float = Float.NaN
    override var isFrameRateFromParent: Boolean = false

    override fun inverseTransform(matrix: Matrix) = Unit
}
