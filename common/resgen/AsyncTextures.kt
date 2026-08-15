package allyouneed.resgen

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Procedurally drawn 16x16 pixel-art textures for the async synthesis block set.
 *
 * Only the seven GT-machine roles without hand-drawn art are generated here (controllers, factory,
 * dedicated cable and the three connectors); the rest of the block set ships hand-drawn PNGs
 * copied by Main.kt. Each procedural role gets its own full-canvas face design - distinct border
 * treatment, panel layout and details - NOT a shared casing recoloured per block. They only stay in
 * the same family through a common edge vocabulary (outline + bevel ring) and per-block colours.
 *
 * The `_formed` variant lights the block's active elements up (hot near-white) and adds an
 * energised inner ring + corner nodes. All textures are fully opaque.
 *
 * The generator reads the block definitions from common/resgen/definitions (see Main.kt). The GT
 * runtime consumes the very same `block/async/` texture files via GTRegistrate machine models, so
 * both definition files intentionally share this output.
 */
object AsyncTextures {
    private const val SIZE = 16

    private data class Palette(
        val base: Int,
        val outline: Int,
        val dark: Int,
        val light: Int,
        val bright: Int,
        val hot: Int,
    )

    fun generate(defs: List<AsyncBlockDef>, texturesBlockDir: Path) {
        texturesBlockDir.toFile().mkdirs()
        for (def in defs) {
            val p = palette(def.color)
            val unformed = draw(def.role, p, formed = false)
            val formed = draw(def.role, p, formed = true)
            ImageIO.write(unformed, "png", texturesBlockDir.resolve("${def.id}.png").toFile())
            ImageIO.write(formed, "png", texturesBlockDir.resolve("${def.id}_formed.png").toFile())
        }
    }

    /**
     * GT AE power hatch front overlays in an AE cable-purple theme.
     *
     * Stock GT dynamo hatches stack three overlay layers on the front face: `overlay_tint`
     * (tier-tinted plate), `overlay_in` (ring) and `overlay_out_emissive` (red glow arrow). We
     * keep GT's own `tinted`/`in` assets (referenced directly by the model, never bundled) and
     * only re-theme the output layer: the red arrow is hue-shifted to AE's cable purple, emitting
     * two files per amperage - `overlay_energy_{n}a_ae.png` and `overlay_energy_{n}a_ae_emissive.png`.
     * Zero-saturation pixels (greys) pass through unchanged; alpha and the brightness gradient are
     * preserved so the arrow keeps its shape and glow.
     */
    fun generateGtAEPowerHatchOverlays(gtSourceDir: Path, texturesOverlayMachineDir: Path) {
        texturesOverlayMachineDir.toFile().mkdirs()
        Files.list(gtSourceDir).use { stream ->
            stream.filter { p ->
                val name = p.fileName.toString()
                name.startsWith("overlay_energy_") && name.contains("_out") && name.endsWith(".png")
            }.forEach { src ->
                val img = ImageIO.read(src.toFile())
                for (x in 0 until img.width) {
                    for (y in 0 until img.height) {
                        val argb = img.getRGB(x, y)
                        val a = argb ushr 24 and 0xFF
                        if (a == 0) continue
                        val r = argb shr 16 and 0xFF
                        val g = argb shr 8 and 0xFF
                        val b = argb and 0xFF
                        val hsb = Color.RGBtoHSB(r, g, b, null)
                        val hue = if (hsb[1] > 0f) AE_PURPLE_HUE else hsb[0]
                        val rgb = Color.HSBtoRGB(hue, hsb[1], hsb[2]) and 0xFFFFFF
                        img.setRGB(x, y, (a shl 24) or rgb)
                    }
                }
                val outName = src.fileName.toString().replace("_out", "_ae")
                ImageIO.write(img, "png", texturesOverlayMachineDir.resolve(outName).toFile())
            }
        }
    }

    /** AE cable purple hue (HSL/HSB degrees -> fraction), AE2's fluix cable accent. */
    private const val AE_PURPLE_HUE = 270f / 360f

    private fun palette(hex: String): Palette {
        val r = hex.substring(1, 3).toInt(16)
        val g = hex.substring(3, 5).toInt(16)
        val b = hex.substring(5, 7).toInt(16)
        return Palette(
            base = argb(r, g, b),
            outline = argb(shade(r, 0.30f), shade(g, 0.30f), shade(b, 0.30f)),
            dark = argb(shade(r, 0.62f), shade(g, 0.62f), shade(b, 0.62f)),
            light = argb(shade(r, 1.15f), shade(g, 1.15f), shade(b, 1.15f)),
            bright = argb(shade(r, 1.38f), shade(g, 1.38f), shade(b, 1.38f)),
            hot = argb(shade(r, 1.85f), shade(g, 1.85f), shade(b, 1.85f)),
        )
    }

    private fun shade(c: Int, f: Float): Int =
        if (f >= 1f) (255 - (255 - c) * (f - 1f)).toInt().coerceIn(0, 255)
        else (c * f).toInt().coerceIn(0, 255)

    private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun draw(role: String, p: Palette, formed: Boolean): BufferedImage {
        val img = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val c = Canvas(img)

        when (role) {
            "CONTROLLER" -> controller(c, p, formed)
            "SWITCH" -> switch(c, p, formed)
            "FACTORY" -> factory(c, p, formed)
            "CABLE" -> cable(c, p, formed)
            "ME_CONNECTOR" -> meConnector(c, p, formed)
            "WAN_CONNECTOR" -> wanConnector(c, p, formed)
            "LAN_CONNECTOR" -> lanConnector(c, p, formed)
            else -> frameBorder(c, p)
        }

        if (formed) {
            // Energised inner ring + corner nodes, shared across roles.
            c.strokeRect(3, 3, 12, 12, p.hot)
            c.fill(3, 3, p.hot)
            c.fill(12, 3, p.hot)
            c.fill(3, 12, p.hot)
            c.fill(12, 12, p.hot)
        }
        return img
    }

    /** Shared edge vocabulary: outline ring + dark bevel + base plate. */
    private fun frameBorder(c: Canvas, p: Palette) {
        c.fillRect(0, 0, 15, 15, p.outline)
        c.fillRect(1, 1, 14, 14, p.dark)
        c.fillRect(2, 2, 13, 13, p.base)
    }

    // -------------------------------------------------------------------------------------------
    // Controllers
    // -------------------------------------------------------------------------------------------

    /** Network controller: dark bezel with a status screen and LED grid. */
    private fun controller(c: Canvas, p: Palette, formed: Boolean) {
        frameBorder(c, p)
        c.fillRect(2, 2, 13, 13, p.dark)
        c.fillRect(4, 4, 11, 10, p.outline)
        c.fillRect(5, 5, 10, 9, p.dark)
        val on = if (formed) p.hot else p.bright
        for (x in intArrayOf(6, 8)) for (y in intArrayOf(5, 7, 9)) {
            c.fill(x, y, on)
        }
        c.fillRect(4, 12, 11, 12, if (formed) p.hot else p.bright)
        c.fill(5, 12, p.hot)
        c.fill(10, 12, p.hot)
    }

    /** Network switch: patch panel with two columns of port LEDs. */
    private fun switch(c: Canvas, p: Palette, formed: Boolean) {
        frameBorder(c, p)
        c.fillRect(4, 3, 6, 12, p.dark)
        c.fillRect(9, 3, 11, 12, p.dark)
        val on = if (formed) p.hot else p.bright
        for (x in intArrayOf(5, 10)) for (y in intArrayOf(4, 7, 10)) {
            c.fill(x, y, on)
        }
        c.fillRect(2, 3, 13, 3, p.bright)
        c.fillRect(2, 12, 13, 12, p.bright)
        c.fill(5, 13, p.hot)
        c.fill(10, 13, p.hot)
    }

    /** Factory: gear with input/output arrows and a conveyor base. */
    private fun factory(c: Canvas, p: Palette, formed: Boolean) {
        frameBorder(c, p)
        val g = if (formed) p.hot else p.bright
        // Input arrow (from left).
        c.fillRect(2, 7, 4, 7, g)
        c.fill(4, 6, g)
        c.fill(4, 8, g)
        // Output arrow (to right).
        c.fillRect(11, 7, 13, 7, g)
        c.fill(10, 6, g)
        c.fill(10, 8, g)
        // Gear.
        c.circle(7, 7, 2, g)
        c.fill(7, 4, g)
        c.fill(8, 4, g)
        c.fill(7, 10, g)
        c.fill(8, 10, g)
        c.fill(4, 7, g)
        c.fill(4, 8, g)
        c.fill(10, 7, g)
        c.fill(10, 8, g)
        c.fillRect(7, 7, 8, 8, p.outline)
        // Conveyor.
        c.fillRect(2, 12, 13, 13, p.dark)
        c.fill(3, 12, p.bright)
        c.fill(6, 12, p.bright)
        c.fill(9, 12, p.bright)
        c.fill(12, 12, p.bright)
    }

    // -------------------------------------------------------------------------------------------
    // Link / connectors / interface
    // -------------------------------------------------------------------------------------------

    /** Dedicated cable: four tapered arms around a hot core. */
    private fun cable(c: Canvas, p: Palette, formed: Boolean) {
        frameBorder(c, p)
        val arm = if (formed) p.hot else p.bright
        c.fillRect(6, 2, 9, 4, arm)
        c.fillRect(6, 11, 9, 13, arm)
        c.fillRect(2, 6, 4, 9, arm)
        c.fillRect(11, 6, 13, 9, arm)
        c.fillRect(5, 5, 10, 10, arm)
        c.strokeRect(4, 4, 11, 11, p.dark)
    }

    /** ME connector: bus plate with a 3x3 grid of slots. */
    private fun meConnector(c: Canvas, p: Palette, formed: Boolean) {
        frameBorder(c, p)
        c.fillRect(3, 3, 12, 12, p.dark)
        c.strokeRect(2, 2, 13, 13, p.bright)
        val on = if (formed) p.hot else p.bright
        for (cx in intArrayOf(4, 7, 10)) for (cy in intArrayOf(4, 7, 10)) {
            c.fill(cx, cy, p.base)
            c.fill(cx + 1, cy, p.base)
            c.fill(cx, cy + 1, p.base)
            c.fill(cx + 1, cy + 1, p.base)
            c.fill(cx, cy, on)
        }
    }

    /** WAN connector: antenna on a wide link port with three bars. */
    private fun wanConnector(c: Canvas, p: Palette, formed: Boolean) {
        frameBorder(c, p)
        c.fillRect(7, 1, 8, 2, p.dark)
        c.fill(7, 1, if (formed) p.hot else p.bright)
        c.fillRect(3, 4, 12, 10, p.dark)
        c.strokeRect(3, 4, 12, 10, p.bright)
        val on = if (formed) p.hot else p.bright
        c.fillRect(5, 5, 10, 5, on)
        c.fillRect(5, 7, 10, 7, on)
        c.fillRect(5, 9, 10, 9, on)
        c.fill(2, 5, p.bright)
        c.fill(2, 9, p.bright)
        c.fill(13, 5, p.bright)
        c.fill(13, 9, p.bright)
    }

    /** LAN connector: inward arrows meeting at a link node. */
    private fun lanConnector(c: Canvas, p: Palette, formed: Boolean) {
        frameBorder(c, p)
        val on = if (formed) p.hot else p.bright
        c.fillRect(2, 7, 4, 7, on)
        c.fill(4, 6, on)
        c.fill(4, 8, on)
        c.fillRect(11, 7, 13, 7, on)
        c.fill(10, 6, on)
        c.fill(10, 8, on)
        c.fillRect(6, 6, 9, 9, on)
        c.strokeRect(5, 5, 10, 10, p.dark)
    }

    // -------------------------------------------------------------------------------------------
    // Canvas helpers
    // -------------------------------------------------------------------------------------------

    private class Canvas(private val img: BufferedImage) {
        fun set(x: Int, y: Int, color: Int) {
            if (x in 0 until SIZE && y in 0 until SIZE) {
                img.setRGB(x, y, color)
            }
        }

        fun fill(x: Int, y: Int, color: Int) = set(x, y, color)

        fun fillRect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
            for (y in y0..y1) for (x in x0..x1) set(x, y, color)
        }

        fun strokeRect(x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
            for (x in x0..x1) {
                set(x, y0, color)
                set(x, y1, color)
            }
            for (y in y0..y1) {
                set(x0, y, color)
                set(x1, y, color)
            }
        }

        fun circle(cx: Int, cy: Int, r: Int, color: Int) {
            for (y in (cy - r)..(cy + r)) {
                for (x in (cx - r)..(cx + r)) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy <= r * r) set(x, y, color)
                }
            }
        }
    }
}
