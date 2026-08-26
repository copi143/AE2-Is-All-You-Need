package minecraftx.text

import minecraftx.compose.text.msdf.GlyphAtlas
import minecraftx.compose.text.msdf.GlyphKey
import minecraftx.compose.text.msdf.MsdfGenerator
import minecraftx.compose.text.msdf.MsdfPoint
import minecraftx.compose.text.msdf.SystemFonts
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MsdfGeneratorTest {

    @Test
    fun `filled square is inside at center and outside at corner`() {
        val square = listOf(
            MsdfPoint(3f, 3f),
            MsdfPoint(13f, 3f),
            MsdfPoint(13f, 13f),
            MsdfPoint(3f, 13f),
            MsdfPoint(3f, 3f),
        )
        val edges = MsdfGenerator.colorEdges(listOf(square))
        val bmp = MsdfGenerator.generate(edges, 16, 16, 0f, 0f, 2f)
        assertTrue(median(bmp.pixels, 16, 8, 8) > 140, "center should be inside")
        assertTrue(median(bmp.pixels, 16, 0, 0) < 80, "corner should be outside")
    }

    @Test
    fun `system font can rasterize latin and cjk`() {
        val chain = SystemFonts.resolve(24f)
        val a = MsdfGenerator.generate(chain.faceFor('A'.code), 'A'.code)
        assertTrue(a != null && a.width > 2 && a.height > 2)
        val zhong = MsdfGenerator.generate(chain.faceFor('中'.code), '中'.code)
        assertTrue(zhong != null && zhong.width > 2 && zhong.height > 2)
    }

    @Test
    fun `font glyph fill is solid not inverted`() {
        val chain = SystemFonts.resolve(48f)
        val h = MsdfGenerator.generate(chain.faceFor('H'.code), 'H'.code)!!
        assertTrue(median(h.pixels, h.width, h.width / 2, h.height / 2) > 140, "H stem should be inside")
        assertTrue(median(h.pixels, h.width, 0, 0) < 80, "H padding should be outside")
        val o = MsdfGenerator.generate(chain.faceFor('O'.code), 'O'.code)!!
        assertTrue(median(o.pixels, o.width, o.width / 2, o.height / 2) < 110, "O hole should be outside")
    }

    @Test
    fun `atlas packs sequential glyphs without overlap`() {
        val atlas = GlyphAtlas(initialSize = 64, maxSize = 128)
        val edges = MsdfGenerator.colorEdges(
            listOf(
                listOf(
                    MsdfPoint(1f, 1f), MsdfPoint(6f, 1f),
                    MsdfPoint(6f, 6f), MsdfPoint(1f, 6f), MsdfPoint(1f, 1f),
                ),
            ),
        )
        val bmp = MsdfGenerator.generate(edges, 8, 8, 0f, 0f, 2f)
        val a = atlas.pack(GlyphKey("t", 1), bmp)!!
        val b = atlas.pack(GlyphKey("t", 2), bmp)!!
        assertTrue(a.x + a.width <= b.x || a.y != b.y)
    }

    private fun median(px: ByteArray, w: Int, x: Int, y: Int): Int {
        val i = (y * w + x) * 4
        val r = px[i].toInt() and 0xFF
        val g = px[i + 1].toInt() and 0xFF
        val b = px[i + 2].toInt() and 0xFF
        return listOf(r, g, b).sorted()[1]
    }
}
