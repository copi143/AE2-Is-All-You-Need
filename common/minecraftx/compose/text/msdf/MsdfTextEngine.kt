package minecraftx.compose.text.msdf

import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.msdftext.AtlasSlot
import allyouneed.client.msdftext.GlyphAtlas
import allyouneed.client.msdftext.GlyphKey
import allyouneed.client.msdftext.MsdfGenerator
import allyouneed.client.msdftext.SystemFonts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import minecraftx.compose.text.McSpanStyle
import minecraftx.compose.text.McStyledString
import minecraftx.compose.text.McTextEngine
import minecraftx.compose.text.McTextLayout
import minecraftx.compose.text.TextWrap
import kotlin.math.roundToInt

class MsdfTextEngine(
    override val id: String = "msdf",
    sizePx: Float = 12f,
) : McTextEngine {

    private val fonts = SystemFonts.resolve(sizePx)
    private val atlas = GlyphAtlas()
    private val renderer = MsdfRenderer(atlas)
    private var uploadsLeft = 0

    override val lineHeight: Int = fonts.lineHeight

    override fun layout(text: McStyledString, maxWidth: Int, singleLine: Boolean): McTextLayout =
        TextWrap.layout(text, maxWidth, singleLine, lineHeight) { cp, _ ->
            fonts.advance(cp).roundToInt()
        }

    override fun widthOf(text: String, style: McSpanStyle?): Int {
        if (text.isEmpty()) return 0
        var w = 0f
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            w += fonts.advance(cp)
            i += Character.charCount(cp)
        }
        return w.roundToInt()
    }

    override fun indexAtWidth(text: String, width: Int, style: McSpanStyle?): Int {
        if (text.isEmpty() || width <= 0) return 0
        var x = 0f
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val adv = fonts.advance(cp)
            if (x + adv > width) return i
            x += adv
            i += Character.charCount(cp)
        }
        return text.length
    }

    override fun DrawScope.paint(layout: McTextLayout, fallbackColor: Color) {
        val g = McGraphics.current ?: return
        if (!renderer.ready()) return
        uploadsLeft = UPLOAD_BUDGET
        val poseScale = kotlin.math.abs(g.pose().last().pose().m00()).coerceAtLeast(1f)
        val spr = (MsdfGenerator.PX_RANGE * fonts.toDraw * poseScale).coerceAtLeast(1f)
        renderer.begin(g, spr)
        val fbArgb = fallbackColor.toArgb()
        val decorations = ArrayList<IntArray>()
        for ((li, line) in layout.lines.withIndex()) {
            val top = (li * layout.lineHeight).toFloat()
            for (run in line.runs) {
                val argb = run.style?.color?.toArgb() ?: fbArgb
                val a = ((argb ushr 24) and 0xFF) / 255f
                val r = ((argb ushr 16) and 0xFF) / 255f
                val gr = ((argb ushr 8) and 0xFF) / 255f
                val b = (argb and 0xFF) / 255f
                val weight = if (run.style?.bold == true) BOLD_WEIGHT else 0f
                val shear = if (run.style?.italic == true) ITALIC_SHEAR * fonts.ascent else 0f
                var pen = run.x.toFloat()
                var i = 0
                val s = run.text
                while (i < s.length) {
                    val cp = s.codePointAt(i)
                    val face = fonts.faceFor(cp)
                    val adv = face.advance(cp)
                    val slot = glyph(cp)
                    if (slot != null) {
                        val inv = 1f / atlas.size
                        val s = fonts.toDraw
                        val x0 = pen + slot.originX * s
                        val y0 = top + fonts.ascent + slot.originY * s
                        renderer.quad(
                            x0, y0, x0 + slot.width * s, y0 + slot.height * s,
                            slot.x * inv, slot.y * inv,
                            (slot.x + slot.width) * inv, (slot.y + slot.height) * inv,
                            r, gr, b, a, shear, weight,
                        )
                    }
                    pen += adv
                    i += Character.charCount(cp)
                }
                val runW = (pen - run.x).roundToInt()
                if (run.style?.underline == true) {
                    val y = (top + layout.lineHeight - 1f).roundToInt()
                    decorations += intArrayOf(run.x, y, run.x + runW, y + 1, argb)
                }
                if (run.style?.strikethrough == true) {
                    val y = (top + layout.lineHeight * 0.5f).roundToInt()
                    decorations += intArrayOf(run.x, y, run.x + runW, y + 1, argb)
                }
            }
        }
        renderer.flush()
        for (d in decorations) g.fill(d[0], d[1], d[2], d[3], d[4])
    }

    private fun glyph(cp: Int): AtlasSlot? {
        if (cp == ' '.code || cp == '\n'.code || cp == '\t'.code) return null
        val face = fonts.faceFor(cp)
        val key = GlyphKey(face.family, cp)
        atlas[key]?.let { return it }
        if (uploadsLeft <= 0) return null
        val bmp = runCatching { MsdfGenerator.generate(fonts.genFace(cp), cp) }.getOrNull() ?: return null
        val slot = atlas.pack(key, bmp) ?: return null
        atlas.ensureTexture()
        atlas.upload(slot)
        uploadsLeft--
        return slot
    }

    private companion object {
        const val UPLOAD_BUDGET = 32
        const val BOLD_WEIGHT = 0.1f
        const val ITALIC_SHEAR = 0.25f
    }
}
