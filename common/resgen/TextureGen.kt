package allyouneed.resgen

import com.github.ajalt.colormath.model.JzCzHz
import com.github.ajalt.colormath.model.RGB
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists

@DslMarker
annotation class TextureGenDsl

@TextureGenDsl
class TextureLayers {
    internal data class Layer(
        val template: String,
        val colors: List<RGB>?,
        val levels: IntRange?,
        val tint: Boolean,
    )

    internal val layers = mutableListOf<Layer>()
    var frameTime: Int = 4
    var interpolate: Boolean = true

    /** Layers are composited in declaration order, from bottom to top. */
    fun layer(template: String, color: String? = null, levels: IntRange? = null, tint: Boolean = false) {
        require(levels == null || !levels.isEmpty()) { "levels must not be empty" }
        layers += Layer(template, color?.let { listOf(RGB(it)) }, levels, tint)
    }

    /** Each color produces an animation frame; tint uses flat color with the original alpha. */
    fun layer(template: String, colors: List<String>, tint: Boolean = false) {
        require(colors.isNotEmpty()) { "colors must not be empty" }
        layers += Layer(template, colors.map { RGB(it) }, null, tint)
        animated = true
    }

    internal var animated = false
        private set
}

@TextureGenDsl
class TextureGen(private val output: Path) {
    private data class Entry(
        val sourceDir: Path,
        val sourceHz: JzCzHz?,
        val outputPrefix: String,
        val dir: String,
        val layers: List<TextureLayers.Layer>,
        val levels: IntRange?,
        val animated: Boolean,
        val frameCount: Int,
        val frameTime: Int,
        val interpolate: Boolean,
    )

    private val entries = mutableListOf<Entry>()
    private var sourceDir: Path? = null
    private var sourceColorHz: JzCzHz? = null

    /** Binds a source directory and optional base color; restores the outer scope even on failure. */
    fun source(dir: Path, color: String? = null, init: TextureGen.() -> Unit) {
        val previousDir = sourceDir
        val previousColor = sourceColorHz
        val scopedColor = color?.let { RGB(it).toJzCzHz() } ?: previousColor
        sourceDir = dir
        sourceColorHz = scopedColor
        try {
            init()
        } finally {
            sourceDir = previousDir
            sourceColorHz = previousColor
        }
    }

    /** Resolves [dir] against the enclosing source directory, inheriting its color unless overridden. */
    fun source(dir: String, color: String? = null, init: TextureGen.() -> Unit) {
        val parent = sourceDir ?: error("Relative source() requires an enclosing source scope")
        source(parent.resolve(dir), color, init)
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
    fun deriveTemplate(
        srcDir: Path = sourceDir ?: error("Call deriveTemplate() inside source {} or specify srcDir"),
        source: String,
        themeFrom: String,
        themeTo: String,
        output: String,
    ) {
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

    /** Level layers read `<template>_<level>.png` and produce `<name>_<level>.png`. */
    fun layered(name: String, dir: String = "block", init: TextureLayers.() -> Unit) {
        val spec = TextureLayers().apply(init)
        require(spec.layers.isNotEmpty()) { "Texture $name must have at least one layer" }
        require(spec.frameTime > 0) { "frameTime must be positive" }
        val levels = spec.layers.mapNotNull { it.levels }.distinct()
        require(levels.size <= 1) { "Layers of $name must use the same levels" }
        val frameCounts = spec.layers.mapNotNull { it.colors?.size }.filter { it > 1 }.distinct()
        require(frameCounts.size <= 1) { "Animated layers of $name must have the same frame count" }
        require(sourceColorHz != null || spec.layers.none { it.colors != null && !it.tint }) {
            "Texture $name needs a source color for recoloring; set color in source {}"
        }
        entries += Entry(
            sourceDir ?: error("Declare layered() inside source {}"), sourceColorHz, name, dir,
            spec.layers.toList(), levels.singleOrNull(), spec.animated,
            frameCounts.singleOrNull() ?: 1, spec.frameTime, spec.interpolate,
        )
    }

    fun generate() {
        for (entry in entries) generate(entry)
    }

    private fun generate(entry: Entry) {
        val images = mutableMapOf<String, BufferedImage>()
        val variants = entry.levels?.map { it to "_$it" } ?: listOf(null to "")
        for ((level, suffix) in variants) {
            val sources = entry.layers.map { layer ->
                val template = layer.template + if (layer.levels != null) "_$level" else ""
                images.getOrPut(template) {
                    val file = entry.sourceDir.resolve("$template.png")
                    check(file.exists()) { "Missing layer $file for ${entry.outputPrefix}" }
                    ensureArgb(ImageIO.read(file.toFile()))
                }
            }
            fun frame(index: Int): BufferedImage {
                val layers = entry.layers.zip(sources).map { (layer, image) ->
                    val color = layer.colors?.let { it[if (it.size == 1) 0 else index] }
                    when {
                        color == null -> image
                        layer.tint -> tint(image, color)
                        else -> {
                            val (hue, chroma, lightness) = colorTransform(checkNotNull(entry.sourceHz), color.toJzCzHz())
                            recolorImage(image, hue, chroma, lightness)
                        }
                    }
                }
                return if (layers.size == 1) layers.single() else composite(*layers.toTypedArray())
            }
            val image = if (entry.animated) {
                val width = sources.first().width
                val height = sources.first().height
                val strip = BufferedImage(width, height * entry.frameCount, BufferedImage.TYPE_INT_ARGB)
                val graphics = strip.createGraphics()
                try {
                    for (i in 0 until entry.frameCount) graphics.drawImage(frame(i), 0, i * height, null)
                } finally {
                    graphics.dispose()
                }
                strip
            } else {
                frame(0)
            }
            writePng(image, entry.outputPrefix, suffix, entry.dir)
            if (entry.animated) {
                writeAnimationMcmeta(
                    entry.outputPrefix + suffix, entry.frameCount, entry.frameTime, entry.interpolate, entry.dir,
                )
            }
        }
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
        dir: String,
    ) {
        val (outRelative, outName) = splitOutput(outputPrefix, dir)
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
