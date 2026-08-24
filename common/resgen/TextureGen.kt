package allyouneed.resgen

import com.github.ajalt.colormath.model.JzCzHz
import com.github.ajalt.colormath.model.RGB
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists

class TextureGen(private val output: Path) {

    private data class RecolorEntry(
        val sourceTemplate: String,
        val outputPrefix: String,
        val targetColor: RGB,
        val sourceHz: JzCzHz,
    )

    /**
     * bg (no recolor) + mid (recolor) + optional top (no recolor, per level or single)
     * + optional [overlays] (no recolor, always on top, single file each).
     */
    private data class LayeredEntry(
        val bgTemplate: String,
        val midTemplate: String,
        val topTemplate: String?,
        val outputPrefix: String,
        val targetColor: RGB?,
        val levels: IntRange?,
        val overlays: List<String> = emptyList(),
        val dir: String = "block",
        val sourceHz: JzCzHz,
    )

    /**
     * Same layering as [LayeredEntry], but [mid] is recolored once per color in [midColors]
     * and frames are stacked vertically (vanilla/AE2 animation strip). Writes `.png.mcmeta`.
     */
    private data class AnimatedLayeredEntry(
        val bgTemplate: String,
        val midTemplate: String,
        val topTemplate: String,
        val outputPrefix: String,
        val midColors: List<RGB>,
        val frameTime: Int,
        val interpolate: Boolean,
        val overlays: List<String> = emptyList(),
        val sourceHz: JzCzHz,
    )

    /**
     * Like [AnimatedLayeredEntry] but the mid's opaque pixels are tinted flat to each [midColors]
     * entry (alpha preserved) instead of JzCzHz-recolored - for white glow overlays whose shape is
     * cycled through the gradient. Composites bg + tinted mid per frame, stacked as an animation
     * strip with `.png.mcmeta`.
     */
    private data class AnimatedTintEntry(
        val bgTemplate: String,
        val midTemplate: String,
        val outputPrefix: String,
        val midColors: List<RGB>,
        val frameTime: Int,
        val interpolate: Boolean,
    )

    private val entries = mutableListOf<RecolorEntry>()
    private val layered = mutableListOf<LayeredEntry>()
    private val animatedLayered = mutableListOf<AnimatedLayeredEntry>()
    private val animatedTints = mutableListOf<AnimatedTintEntry>()
    private var sourceDir: Path? = null
    private var sourceColorHz: JzCzHz? = null

    fun source(dir: Path, color: String) {
        sourceDir = dir
        sourceColorHz = RGB(color).toJzCzHz()
    }

    /**
     * Derive a source template by retinting it from the theme of [themeFrom] to the theme of
     * [themeTo], writing `<srcDir>/<output>.png`. [source] only provides pixel structure.
     *
     * Unlike [recolorImage]'s proportional chroma scaling, chroma is shifted additively
     * (`c + toC - fromC`): a near-neutral source plate keeps its low per-pixel chroma under
     * scaling and comes out washed out, while the shift lifts the whole plate onto the target
     * theme's saturation level. Lightness stays proportional. Idempotent: never reads its own
     * output, safe to run every generateAssets pass.
     */
    fun deriveTemplate(srcDir: Path, source: String, themeFrom: String, themeTo: String, output: String) {
        val srcFile = srcDir.resolve("$source.png")
        val fromFile = srcDir.resolve("$themeFrom.png")
        val toFile = srcDir.resolve("$themeTo.png")
        if (!srcFile.exists() || !fromFile.exists() || !toFile.exists()) {
            println("[texture] missing derive inputs for $output (need $source/$themeFrom/$themeTo in $srcDir)")
            return
        }
        val from = themePixelHz(fromFile)
        val to = themePixelHz(toFile)
        val derived = shiftImage(
            ensureArgb(ImageIO.read(srcFile.toFile())),
            hueShift = to.h - from.h,
            chromaShift = to.c - from.c,
            lightnessScale = if (from.j > 0.001f) to.j / from.j else 1f,
        )
        ImageIO.write(derived, "png", srcDir.resolve("$output.png").toFile())
        println("[texture] derived $srcDir/$output.png ($themeFrom -> $themeTo)")
    }

    /** Theme color of a plate: the bottom-right pixel (see [deriveTemplate] for the convention). */
    private fun themePixelHz(file: Path): JzCzHz {
        val img = ensureArgb(ImageIO.read(file.toFile()))
        val argb = img.getRGB(img.width - 1, img.height - 1)
        require((argb ushr 24) and 0xFF != 0) { "bottom-right theme pixel is transparent in $file" }
        return RGB(
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        ).toJzCzHz()
    }

    private fun currentSourceHz(): JzCzHz =
        sourceColorHz ?: error("Call source() before registering texture targets")

    fun targetSingle(sourceTemplate: String, outputPrefix: String, color: String) {
        entries += RecolorEntry(sourceTemplate, outputPrefix, RGB(color), currentSourceHz())
    }

    /**
     * Composite: [bg] (bottom, no recolor) + [mid] (recolored if [color] set) + optional [top] (no recolor)
     * + optional [overlays] (no recolor, stacked last).
     * With [levels], top is `{topTemplate}_{level}.png` and outputs `{outputPrefix}_{level}.png`.
     * Without levels / without top: single output `{outputPrefix}.png` (bg+mid[+overlays]).
     */
    fun layeredTarget(
        bg: String,
        mid: String,
        top: String? = null,
        outputPrefix: String,
        color: String?,
        levels: IntRange? = 0..4,
        overlays: List<String> = emptyList(),
        dir: String = "block",
    ) {
        layered += LayeredEntry(
            bg, mid, top, outputPrefix, color?.let { RGB(it) }, levels, overlays, dir, currentSourceHz(),
        )
    }

    /**
     * AE2-style animated texture: vertical frame strip + `.png.mcmeta`.
     * Each frame composites bg + mid(recolored to [midColors][i]) + top + overlays.
     */
    fun layeredAnimated(
        bg: String,
        mid: String,
        top: String,
        outputPrefix: String,
        midColors: List<String>,
        frameTime: Int = 4,
        interpolate: Boolean = true,
        overlays: List<String> = emptyList(),
    ) {
        require(midColors.isNotEmpty()) { "midColors must not be empty" }
        animatedLayered += AnimatedLayeredEntry(
            bg, mid, top, outputPrefix,
            midColors.map { RGB(it) },
            frameTime, interpolate, overlays,
            currentSourceHz(),
        )
    }

    /**
     * White-overlay gradient cycle: [mid] is tinted flat to each [midColors][i] (opaque pixels keep
     * their alpha) and composited over [bg], stacked into a vertical animation strip.
     */
    fun layeredAnimatedTint(
        bg: String,
        mid: String,
        outputPrefix: String,
        midColors: List<String>,
        frameTime: Int = 4,
        interpolate: Boolean = true,
    ) {
        require(midColors.isNotEmpty()) { "midColors must not be empty" }
        animatedTints += AnimatedTintEntry(
            bg, mid, outputPrefix,
            midColors.map { RGB(it) },
            frameTime, interpolate,
        )
    }

    fun generate() {
        val srcDir = sourceDir ?: error("Call source() first")

        for (entry in entries) {
            generateRecolor(srcDir, entry)
        }
        for (entry in layered) {
            generateLayered(srcDir, entry)
        }
        for (entry in animatedLayered) {
            generateAnimatedLayered(srcDir, entry)
        }
        for (entry in animatedTints) {
            generateAnimatedTint(srcDir, entry)
        }
    }

    private fun generateRecolor(srcDir: Path, entry: RecolorEntry) {
        val srcHz = entry.sourceHz
        val targetHz = entry.targetColor.toJzCzHz()
        val (hueShift, chromaScale, lightnessScale) = colorTransform(srcHz, targetHz)

        val file = srcDir.resolve("${entry.sourceTemplate}.png")
        if (!file.exists()) return
        val dstImage = recolorImage(ImageIO.read(file.toFile()), hueShift, chromaScale, lightnessScale)
        writePng(dstImage, entry.outputPrefix, "")
    }

    private fun generateLayered(srcDir: Path, entry: LayeredEntry) {
        val srcHz = entry.sourceHz
        val bgFile = srcDir.resolve("${entry.bgTemplate}.png")
        val midFile = srcDir.resolve("${entry.midTemplate}.png")
        if (!bgFile.exists() || !midFile.exists()) {
            println("[texture] missing bg/mid for ${entry.outputPrefix}")
            return
        }

        val bg = ensureArgb(ImageIO.read(bgFile.toFile()))
        val midSrc = ensureArgb(ImageIO.read(midFile.toFile()))

        val mid: BufferedImage = if (entry.targetColor != null) {
            val (hueShift, chromaScale, lightnessScale) = colorTransform(srcHz, entry.targetColor.toJzCzHz())
            recolorImage(midSrc, hueShift, chromaScale, lightnessScale)
        } else {
            midSrc
        }

        val overlayImages = entry.overlays.mapNotNull { name ->
            val file = srcDir.resolve("$name.png")
            if (!file.exists()) {
                println("[texture] missing overlay $file")
                null
            } else {
                ensureArgb(ImageIO.read(file.toFile()))
            }
        }

        val tops: List<Pair<String, BufferedImage?>> = when {
            entry.topTemplate == null -> listOf("" to null)
            entry.levels != null -> entry.levels.map { level ->
                val file = srcDir.resolve("${entry.topTemplate}_$level.png")
                if (!file.exists()) {
                    println("[texture] missing top $file")
                    "_$level" to null
                } else {
                    "_$level" to ensureArgb(ImageIO.read(file.toFile()))
                }
            }

            else -> {
                val file = srcDir.resolve("${entry.topTemplate}.png")
                if (!file.exists()) {
                    println("[texture] missing top $file")
                    emptyList()
                } else {
                    listOf("" to ensureArgb(ImageIO.read(file.toFile())))
                }
            }
        }

        for ((suffix, top) in tops) {
            if (entry.topTemplate != null && top == null) continue
            val layers = buildList {
                add(bg)
                add(mid)
                if (top != null) add(top)
                addAll(overlayImages)
            }
            val composed = composite(*layers.toTypedArray())
            writePng(composed, entry.outputPrefix, suffix, entry.dir)
        }
    }

    private fun generateAnimatedLayered(srcDir: Path, entry: AnimatedLayeredEntry) {
        val srcHz = entry.sourceHz
        val bgFile = srcDir.resolve("${entry.bgTemplate}.png")
        val midFile = srcDir.resolve("${entry.midTemplate}.png")
        val topFile = srcDir.resolve("${entry.topTemplate}.png")
        if (!bgFile.exists() || !midFile.exists() || !topFile.exists()) {
            println("[texture] missing layers for animated ${entry.outputPrefix}")
            return
        }

        val bg = ensureArgb(ImageIO.read(bgFile.toFile()))
        val midSrc = ensureArgb(ImageIO.read(midFile.toFile()))
        val top = ensureArgb(ImageIO.read(topFile.toFile()))
        val overlayImages = entry.overlays.mapNotNull { name ->
            val file = srcDir.resolve("$name.png")
            if (!file.exists()) {
                println("[texture] missing overlay $file")
                null
            } else {
                ensureArgb(ImageIO.read(file.toFile()))
            }
        }

        val frameH = bg.height
        val frameW = bg.width
        val strip = BufferedImage(frameW, frameH * entry.midColors.size, BufferedImage.TYPE_INT_ARGB)
        val g = strip.createGraphics()

        for ((i, color) in entry.midColors.withIndex()) {
            val (hueShift, chromaScale, lightnessScale) = colorTransform(srcHz, color.toJzCzHz())
            val mid = recolorImage(midSrc, hueShift, chromaScale, lightnessScale)
            val layers = buildList {
                add(bg)
                add(mid)
                add(top)
                addAll(overlayImages)
            }
            val frame = composite(*layers.toTypedArray())
            g.drawImage(frame, 0, i * frameH, null)
        }
        g.dispose()

        writePng(strip, entry.outputPrefix, "")
        writeAnimationMcmeta(entry.outputPrefix, entry.midColors.size, entry.frameTime, entry.interpolate)
    }

    private fun generateAnimatedTint(srcDir: Path, entry: AnimatedTintEntry) {
        val bgFile = srcDir.resolve("${entry.bgTemplate}.png")
        val midFile = srcDir.resolve("${entry.midTemplate}.png")
        if (!bgFile.exists() || !midFile.exists()) {
            println("[texture] missing layers for animated tint ${entry.outputPrefix}")
            return
        }

        val bg = ensureArgb(ImageIO.read(bgFile.toFile()))
        val mid = ensureArgb(ImageIO.read(midFile.toFile()))

        val frameH = bg.height
        val frameW = bg.width
        val strip = BufferedImage(frameW, frameH * entry.midColors.size, BufferedImage.TYPE_INT_ARGB)
        val g = strip.createGraphics()

        for ((i, color) in entry.midColors.withIndex()) {
            val frame = composite(bg, tint(mid, color))
            g.drawImage(frame, 0, i * frameH, null)
        }
        g.dispose()

        writePng(strip, entry.outputPrefix, "")
        writeAnimationMcmeta(entry.outputPrefix, entry.midColors.size, entry.frameTime, entry.interpolate)
    }

    /** Replaces the opaque pixels of [src] with [color], preserving their alpha. */
    private fun tint(src: BufferedImage, color: RGB): BufferedImage {
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val r = (color.r * 255f + 0.5f).toInt().coerceIn(0, 255)
        val g = (color.g * 255f + 0.5f).toInt().coerceIn(0, 255)
        val b = (color.b * 255f + 0.5f).toInt().coerceIn(0, 255)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val argb = src.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    dst.setRGB(x, y, 0)
                } else {
                    dst.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }
        }
        return dst
    }

    private fun writeAnimationMcmeta(
        outputPrefix: String,
        frameCount: Int,
        frameTime: Int,
        interpolate: Boolean,
    ) {
        val (outRelative, outName) = splitOutput(outputPrefix, "block")
        val outDir = output.resolve(outRelative)
        outDir.toFile().mkdirs()

        // Same structure as AE2 crafting light / controller animations
        val frames = (0 until frameCount).joinToString(",\n") { i ->
            """		{
			"index": $i,
			"time": $frameTime
		}"""
        }
        val json = """
{
  "animation": {
    "interpolate": $interpolate,
    "frames": [
$frames
    ]
  }
}
""".trimStart()
        outDir.resolve("$outName.png.mcmeta").toFile().writeText(json)
    }

    private fun recolorImage(
        srcImage: BufferedImage,
        hueShift: Float,
        chromaScale: Float,
        lightnessScale: Float,
    ): BufferedImage {
        val src = ensureArgb(srcImage)
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val argb = src.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    dst.setRGB(x, y, 0)
                    continue
                }
                val r = ((argb shr 16) and 0xFF) / 255f
                val g = ((argb shr 8) and 0xFF) / 255f
                val b = (argb and 0xFF) / 255f
                val pixelHz = RGB(r, g, b).toJzCzHz()
                val mapped = gamutMap(
                    JzCzHz(
                        pixelHz.j * lightnessScale,
                        pixelHz.c * chromaScale,
                        pixelHz.h + hueShift,
                    ),
                )
                val rgb = mapped.toSRGB().toRGBInt()
                // Preserve original alpha
                val out = (a shl 24) or (rgb.argb.toInt() and 0x00FFFFFF)
                dst.setRGB(x, y, out)
            }
        }
        return dst
    }

    /**
     * Additive JzCzHz shift: hue/chroma translate, lightness scales. Unlike [recolorImage],
     * chroma is not multiplied per-pixel, so near-neutral plates still reach the target
     * saturation instead of staying washed out. Out-of-gamut results are gamut-mapped.
     */
    private fun shiftImage(
        srcImage: BufferedImage,
        hueShift: Float,
        chromaShift: Float,
        lightnessScale: Float,
    ): BufferedImage {
        val src = ensureArgb(srcImage)
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val argb = src.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    dst.setRGB(x, y, 0)
                    continue
                }
                val r = ((argb shr 16) and 0xFF) / 255f
                val g = ((argb shr 8) and 0xFF) / 255f
                val b = (argb and 0xFF) / 255f
                val pixelHz = RGB(r, g, b).toJzCzHz()
                val mapped = gamutMap(
                    JzCzHz(
                        j = pixelHz.j * lightnessScale,
                        c = pixelHz.c + chromaShift,
                        h = pixelHz.h + hueShift,
                    ),
                )
                val rgb = mapped.toSRGB().toRGBInt()
                val out = (a shl 24) or (rgb.argb.toInt() and 0x00FFFFFF)
                dst.setRGB(x, y, out)
            }
        }
        return dst
    }

    /** Porter-Duff SRC_OVER stack: bottom → top. */
    private fun composite(vararg layers: BufferedImage): BufferedImage {
        val w = layers[0].width
        val h = layers[0].height
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var dr = 0f
                var dg = 0f
                var db = 0f
                var da = 0f
                for (layer in layers) {
                    val px = if (x < layer.width && y < layer.height) layer.getRGB(x, y) else 0
                    val sa = ((px ushr 24) and 0xFF) / 255f
                    if (sa == 0f) continue
                    val sr = ((px shr 16) and 0xFF) / 255f
                    val sg = ((px shr 8) and 0xFF) / 255f
                    val sb = (px and 0xFF) / 255f
                    // out = src + dst * (1 - src.a)
                    dr = sr * sa + dr * (1f - sa)
                    dg = sg * sa + dg * (1f - sa)
                    db = sb * sa + db * (1f - sa)
                    da = sa + da * (1f - sa)
                }
                val a = (da * 255f + 0.5f).toInt().coerceIn(0, 255)
                val r = if (da > 1e-6f) ((dr / da) * 255f + 0.5f).toInt().coerceIn(0, 255) else 0
                val g = if (da > 1e-6f) ((dg / da) * 255f + 0.5f).toInt().coerceIn(0, 255) else 0
                val b = if (da > 1e-6f) ((db / da) * 255f + 0.5f).toInt().coerceIn(0, 255) else 0
                dst.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return dst
    }

    private fun ensureArgb(img: BufferedImage): BufferedImage {
        if (img.type == BufferedImage.TYPE_INT_ARGB) return img
        val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(img, 0, 0, null)
        g.dispose()
        return out
    }

    private fun writePng(image: BufferedImage, outputPrefix: String, suffix: String, dir: String = "block") {
        val (outRelative, outName) = splitOutput(outputPrefix, dir)
        val outDir = output.resolve(outRelative)
        outDir.toFile().mkdirs()
        ImageIO.write(image, "png", outDir.resolve("${outName + suffix}.png").toFile())
    }

    /** Splits [prefix] into (textures/&lt;dir&gt;/&lt;subdir&gt;, file name) for output writes. */
    private fun splitOutput(prefix: String, dir: String): Pair<String, String> =
        if ('/' in prefix) {
            "textures/$dir/${prefix.substringBeforeLast('/')}" to prefix.substringAfterLast('/')
        } else {
            "textures/$dir" to prefix
        }

    /** Hue shift / chroma scale / lightness scale mapping [srcHz] towards [targetHz]. */
    private fun colorTransform(srcHz: JzCzHz, targetHz: JzCzHz): Triple<Float, Float, Float> {
        val hueShift = targetHz.h - srcHz.h
        val chromaScale = if (srcHz.c > 0.001f) targetHz.c / srcHz.c else 1f
        val lightnessScale = if (srcHz.j > 0.001f) targetHz.j / srcHz.j else 1f
        return Triple(hueShift, chromaScale, lightnessScale)
    }
}

fun retexture(output: Path, init: TextureGen.() -> Unit) {
    TextureGen(output).apply(init).generate()
}

fun gamutMap(color: JzCzHz): JzCzHz {
    if (color.isInSRGBGamut()) return color
    if (color.c <= 0.001f) return color
    var lo = 0f
    var hi = color.c
    var best = JzCzHz(color.j, 0f, color.h)
    for (i in 0 until 16) {
        val mid = (lo + hi) / 2f
        val candidate = JzCzHz(color.j, mid, color.h)
        if (candidate.isInSRGBGamut()) {
            best = candidate
            lo = mid
        } else {
            hi = mid
        }
    }
    return best
}

private fun JzCzHz.isInSRGBGamut(): Boolean {
    val rgb = toSRGB()
    return rgb.r in 0f..1f && rgb.g in 0f..1f && rgb.b in 0f..1f
}
