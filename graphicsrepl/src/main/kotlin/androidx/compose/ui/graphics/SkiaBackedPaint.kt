/*
 * Copyright 2024 The AE2-Is-All-You-Need Authors
 *
 * Replacement for androidx.compose.ui.graphics.SkiaBackedPaint that does not
 * depend on org.jetbrains.skia native bindings. Painting state is stored in
 * plain Kotlin fields and is later consumed by the Minecraft GraphicsContext
 * implementation, so rendering never touches skiko.
 */

package androidx.compose.ui.graphics

@Suppress("DEPRECATION_ERROR", "DEPRECATION")
class SkiaBackedPaint : Paint {
    private var internalColor: Color = Color.Black
    private var mAlphaMultiplier = 1.0f

    var alphaMultiplier: Float
        get() = mAlphaMultiplier
        set(value) {
            val multiplier = value.coerceIn(0f, 1f)
            internalColor = internalColor.copy(alpha = internalColor.alpha * multiplier)
            mAlphaMultiplier = multiplier
        }

    override var alpha: Float
        get() = internalColor.alpha
        set(value) {
            internalColor = internalColor.copy(alpha = (value * mAlphaMultiplier).coerceIn(0f, 1f))
        }

    override var isAntiAlias: Boolean = true

    override var color: Color
        get() = internalColor
        set(value) {
            internalColor = value
        }

    override var blendMode: BlendMode = BlendMode.SrcOver

    override var style: PaintingStyle = PaintingStyle.Fill

    override var strokeWidth: Float = 0f

    override var strokeCap: StrokeCap = StrokeCap.Butt

    override var strokeJoin: StrokeJoin = StrokeJoin.Round

    override var strokeMiterLimit: Float = 0f

    override var filterQuality: FilterQuality = FilterQuality.Medium

    override var shader: Shader? = null

    override var colorFilter: ColorFilter? = null

    override var pathEffect: PathEffect? = null
}
