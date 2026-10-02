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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.flattenContours
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.joml.Matrix4f

/**
 * Zero-offscreen layer: instead of rendering content into a GPU/offscreen surface, the drawing
 * block is invoked directly against the [Canvas] passed to [drawLayer] (a [McCanvas] bridging to
 * Minecraft's [net.minecraft.client.gui.GuiGraphics]). Layer properties are honoured as far as the
 * immediate-mode canvas allows: position/translation, scale, rotation and alpha (alpha applied via
 * [McCanvas.withAlpha], transform via pose save/concat). Shape clipping is delegated to the canvas.
 */
class PassthroughLayer(
    private var drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
    private var invalidateParentLayer: () -> Unit,
) : OwnedLayer {
    private var position: IntOffset = IntOffset.Zero
    private var size: IntSize = IntSize.Zero
    private var layerAlpha: Float = 1f
    private var scaleX: Float = 1f
    private var scaleY: Float = 1f
    private var rotationZ: Float = 0f
    private var translationX: Float = 0f
    private var translationY: Float = 0f
    private var clip = false
    private var shape: Shape = RectangleShape
    private var density: Density = Density(1f)
    private var layoutDirection = LayoutDirection.Ltr
    private var origin = TransformOrigin.Center
    private var outline: Outline? = null
    private var hitContours: List<List<Offset>> = emptyList()
    private var hitFillType = PathFillType.NonZero
    private val pose = Matrix4f()

    override fun updateLayerProperties(scope: ReusableGraphicsLayerScope) {
        layerAlpha = scope.alpha
        scaleX = scope.scaleX
        scaleY = scope.scaleY
        rotationZ = scope.rotationZ
        translationX = scope.translationX
        translationY = scope.translationY
        clip = scope.clip
        shape = scope.shape
        density = Density(scope.density, scope.fontScale)
        layoutDirection = scope.layoutDirection
        origin = scope.transformOrigin
        updateGeometry()
    }

    override fun isInLayer(position: Offset): Boolean {
        if (!clip) return true
        val current = outline ?: return false
        if (current is Outline.Rectangle) return current.rect.contains(position)
        return ClipGeometry.contains(hitContours, position, hitFillType)
    }

    override fun move(position: IntOffset) {
        this.position = position
    }

    override fun resize(size: IntSize) {
        this.size = size
        updateGeometry()
    }

    private fun updateGeometry() {
        outline = if (clip) shape.createOutline(Size(size.width.toFloat(), size.height.toFloat()), layoutDirection, density) else null
        val path = when (val current = outline) {
            is Outline.Rounded -> Path().apply { addRoundRect(current.roundRect) }
            is Outline.Generic -> current.path
            else -> null
        }
        hitContours = path?.flattenContours(0.25f).orEmpty()
        hitFillType = path?.fillType ?: PathFillType.NonZero
        updateMatrix()
    }

    private fun updateMatrix() {
        val px = size.width * origin.pivotFractionX
        val py = size.height * origin.pivotFractionY
        // NodeCoordinator adds layout position itself when mapping coordinates. Only drawing
        // includes that position; the OwnedLayer matrix describes the extra graphics transform.
        pose.identity().translate(translationX, translationY, 0f)
            .translate(px, py, 0f).rotateZ(Math.toRadians(rotationZ.toDouble()).toFloat())
            .scale(scaleX, scaleY, 1f).translate(-px, -py, 0f)
        pose.get(underlyingMatrix.values)
    }

    override fun drawLayer(canvas: Canvas, parentLayer: GraphicsLayer?) {
        canvas.save()
        try {
            canvas.translate(position.x.toFloat(), position.y.toFloat())
            canvas.concat(underlyingMatrix)
            when (val current = outline) {
                is Outline.Rectangle -> canvas.clipRect(current.rect)
                is Outline.Rounded -> canvas.clipPath(Path().apply { addRoundRect(current.roundRect) })
                is Outline.Generic -> canvas.clipPath(current.path)
                null -> Unit
            }
            drawWithAlpha(canvas, parentLayer)
        } finally {
            canvas.restore()
        }
    }

    private fun drawWithAlpha(canvas: Canvas, parentLayer: GraphicsLayer?) {
        if (layerAlpha >= 1f || canvas !is McCanvas) {
            drawBlock(canvas, parentLayer)
        } else {
            canvas.withAlpha(layerAlpha) { drawBlock(canvas, parentLayer) }
        }
    }

    override fun updateDisplayList() {}

    override fun invalidate() {
        invalidateParentLayer()
    }

    override fun destroy() {}

    override fun mapOffset(point: Offset, inverse: Boolean): Offset {
        if (inverse && (scaleX == 0f || scaleY == 0f)) return Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val matrix = if (inverse) Matrix4f(pose).invert() else pose
        return ClipGeometry.transform(listOf(point), matrix).first()
    }

    override fun mapBounds(rect: MutableRect, inverse: Boolean) {
        val points = listOf(Offset(rect.left, rect.top), Offset(rect.right, rect.top),
            Offset(rect.right, rect.bottom), Offset(rect.left, rect.bottom)).map { mapOffset(it, inverse) }
        rect.left = points.minOf { it.x }
        rect.top = points.minOf { it.y }
        rect.right = points.maxOf { it.x }
        rect.bottom = points.maxOf { it.y }
    }

    override fun reuseLayer(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
    ) {
        this.drawBlock = drawBlock
        this.invalidateParentLayer = invalidateParentLayer
    }

    override fun transform(matrix: Matrix) { matrix *= underlyingMatrix }

    override val underlyingMatrix: Matrix = Matrix()

    override var frameRate: Float = Float.NaN
    override var isFrameRateFromParent: Boolean = false

    override fun inverseTransform(matrix: Matrix) {
        if (scaleX != 0f && scaleY != 0f) matrix *= Matrix(Matrix4f(pose).invert().get(FloatArray(16)))
    }
}
