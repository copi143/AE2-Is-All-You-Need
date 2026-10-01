package minecraftx.compose.text.msdf

import allyouneed.client.compose.platform.McGraphics
import allyouneed.client.msdftext.AtlasSlot
import allyouneed.client.msdftext.GlyphAtlas
import allyouneed.client.msdftext.GlyphKey
import allyouneed.client.msdftext.MsdfGenerator
import allyouneed.client.msdftext.SystemFonts
import androidx.compose.ui.graphics.Color
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

    /** Frees the renderer's GPU objects; they are lazily recreated on the next [paint]. */
    fun releaseGl() = renderer.destroy()

    override fun paint(layout: McTextLayout, fallbackColor: Color, shadow: Boolean) {
        val g = McGraphics.current ?: return
        if (!renderer.ready()) return
        uploadsLeft = UPLOAD_BUDGET
        renderer.begin(g, MsdfGenerator.PX_RANGE)
        val fbArgb = fallbackColor.toArgb()
        val decorations = ArrayList<IntArray>()
        for ((li, line) in layout.lines.withIndex()) {
            val top = (li * layout.lineHeight).toFloat()
            for (run in line.runs) {
                val argb = run.style?.color?.toArgb() ?: fbArgb
                // 阴影与本体同批提交:先阴影(+1,+1,1/4 强度,原版公式),后本体。
                for (pass in if (shadow) 0..1 else 1..1) {
                    val c = if (pass == 0) (argb and 0xFF000000.toInt()) or ((argb and 0xFCFCFC) ushr 2) else argb
                    val off = if (pass == 0) 1f else 0f
                    paintRun(run, top, c, off, off, layout, decorations)
                }
            }
        }
        renderer.flush()
        for (d in decorations) g.fill(d[0], d[1], d[2], d[3], d[4])
    }

    private fun paintRun(
        run: minecraftx.compose.text.StyledRun,
        top: Float,
        argb: Int,
        offX: Float,
        offY: Float,
        layout: McTextLayout,
        decorations: MutableList<IntArray>,
    ) {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val gr = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val weight = if (run.style?.bold == true) BOLD_WEIGHT else 0f
        val shear = if (run.style?.italic == true) ITALIC_SHEAR * fonts.ascent else 0f
        var pen = run.x.toFloat() + offX
        var i = 0
        val s = run.text
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val face = fonts.faceFor(cp)
            val adv = face.advance(cp)
            val slot = glyph(cp)
            if (slot != null) {
                val inv = 1f / atlas.size
                val sc = fonts.toDraw
                val x0 = pen + slot.originX * sc
                val y0 = top + offY + fonts.ascent + slot.originY * sc
                renderer.quad(
                    x0, y0, x0 + slot.width * sc, y0 + slot.height * sc,
                    slot.x * inv, slot.y * inv,
                    (slot.x + slot.width) * inv, (slot.y + slot.height) * inv,
                    r, gr, b, a, shear, weight,
                )
            }
            pen += adv
            i += Character.charCount(cp)
        }
        if (offX == 0f && offY == 0f) {
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
        const val UPLOAD_BUDGET = 256
        const val BOLD_WEIGHT = 0.1f
        const val ITALIC_SHEAR = 0.25f
    }
}
