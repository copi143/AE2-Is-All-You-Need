package allyouneed.client.msdftext

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext
import java.awt.font.GlyphVector
import java.awt.geom.AffineTransform
import kotlin.math.roundToInt

class AwtFontFace(val font: Font, val frc: FontRenderContext) {
    val family: String = font.family
    private val metrics = font.getLineMetrics("Ag中", frc)
    val ascent: Float = metrics.ascent
    val descent: Float = metrics.descent
    val lineHeight: Int = (metrics.ascent + metrics.descent + metrics.leading).roundToInt().coerceAtLeast(1)

    fun canDisplay(cp: Int): Boolean = font.canDisplay(cp)

    fun glyphVector(cp: Int): GlyphVector =
        font.createGlyphVector(frc, Character.toChars(cp))

    fun advance(cp: Int): Float {
        if (cp == '\n'.code) return 0f
        return glyphVector(cp).getGlyphMetrics(0).advance
    }
}

class FontChain(val faces: List<AwtFontFace>, val genPx: Float, val drawPx: Float) {
    val primary: AwtFontFace = faces.first()
    val lineHeight: Int = primary.lineHeight
    val ascent: Float = primary.ascent
    val toDraw: Float = drawPx / genPx
    private val genFaces = HashMap<String, AwtFontFace>()

    fun faceFor(cp: Int): AwtFontFace = faces.firstOrNull { it.canDisplay(cp) } ?: primary

    fun genFace(cp: Int): AwtFontFace {
        val src = faceFor(cp)
        return genFaces.getOrPut(src.family) { AwtFontFace(src.font.deriveFont(genPx), src.frc) }
    }

    fun advance(cp: Int): Float = faceFor(cp).advance(cp)
}

object SystemFonts {
    private val latin = listOf(
        "Inter", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Noto Sans",
        "DejaVu Sans", "Liberation Sans", "FreeSans", "Arial", Font.SANS_SERIF,
    )
    private val cjk = listOf(
        "Noto Sans CJK SC", "Noto Sans SC", "Noto Sans CJK TC",
        "Source Han Sans SC", "Source Han Sans CN", "Source Han Sans",
        "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
        "Hiragino Sans GB", "WenQuanYi Micro Hei", "WenQuanYi Zen Hei",
        "Droid Sans Fallback", "SimHei", "Noto Sans CJK JP",
    )

    const val GEN_PX = 48f

    fun resolve(sizePx: Float, genPx: Float = GEN_PX): FontChain {
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
            .associateBy { it.lowercase() }
        val frc = FontRenderContext(AffineTransform(), true, true)
        val chosen = ArrayList<Font>()
        pick(available, latin)?.let { chosen += sized(it, sizePx) }
        pick(available, cjk)?.let { fontName ->
            val f = sized(fontName, sizePx)
            if (chosen.none { it.family == f.family }) chosen += f
        }
        if (chosen.isEmpty()) chosen += Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(sizePx)
        return FontChain(chosen.map { AwtFontFace(it, frc) }, genPx, sizePx)
    }

    private fun pick(available: Map<String, String>, names: List<String>): String? {
        for (n in names) {
            if (n == Font.SANS_SERIF) return n
            available[n.lowercase()]?.let { return it }
        }
        return null
    }

    private fun sized(family: String, sizePx: Float): Font =
        Font(family, Font.PLAIN, 1).deriveFont(sizePx)
}
