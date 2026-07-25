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
        val levels: IntRange?,
    )

    private val entries = mutableListOf<RecolorEntry>()
    private var sourceDir: Path? = null
    private var sourceColorHz: JzCzHz? = null

    fun source(dir: Path, color: String) {
        sourceDir = dir
        sourceColorHz = RGB(color).toJzCzHz()
    }

    fun target(sourceTemplate: String, outputPrefix: String, color: String, levels: IntRange = 0..4) {
        entries += RecolorEntry(sourceTemplate, outputPrefix, RGB(color), levels)
    }

    fun targetSingle(sourceTemplate: String, outputPrefix: String, color: String) {
        entries += RecolorEntry(sourceTemplate, outputPrefix, RGB(color), null)
    }

    private fun JzCzHz.isInSRGBGamut(): Boolean {
        val rgb = toSRGB()
        return rgb.r in 0f..1f && rgb.g in 0f..1f && rgb.b in 0f..1f
    }

    private fun gamutMap(color: JzCzHz): JzCzHz {
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

    fun generate() {
        val srcDir = sourceDir ?: error("Call source() first")
        val srcHz = sourceColorHz ?: error("Call source() first")

        for (entry in entries) {
            val targetHz = entry.targetColor.toJzCzHz()

            val hueShift = targetHz.h - srcHz.h
            val chromaScale = (if (srcHz.c > 0.001f) targetHz.c / srcHz.c else 1f)

            val srcFile = if (entry.levels != null) {
                entry.levels.map { srcDir.resolve("${entry.sourceTemplate}_$it.png") }
            } else {
                listOf(srcDir.resolve("${entry.sourceTemplate}.png"))
            }

            for ((idx, file) in srcFile.withIndex()) {
                if (!file.exists()) continue
                val suffix = entry.levels?.let { "_${it.first + idx}" } ?: ""

                val srcImage = ImageIO.read(file.toFile())
                val dstImage = BufferedImage(srcImage.width, srcImage.height, BufferedImage.TYPE_INT_ARGB)

                for (y in 0 until srcImage.height) {
                    for (x in 0 until srcImage.width) {
                        val argb = srcImage.getRGB(x, y)
                        val a = ((argb shr 24) and 0xFF) / 255f
                        if (a == 0f) {
                            dstImage.setRGB(x, y, 0)
                            continue
                        }

                        val r = ((argb shr 16) and 0xFF) / 255f
                        val g = ((argb shr 8) and 0xFF) / 255f
                        val b = (argb and 0xFF) / 255f

                        val pixelHz = RGB(r, g, b).toJzCzHz()

                        val cNew = pixelHz.c * chromaScale
                        val hNew = pixelHz.h + hueShift
                        val mapped = gamutMap(JzCzHz(pixelHz.j, cNew, hNew))

                        dstImage.setRGB(x, y, mapped.toSRGB().toRGBInt().argb.toInt())
                    }
                }

                val outDir = output.resolve("textures/block")
                outDir.toFile().mkdirs()
                ImageIO.write(dstImage, "png", outDir.resolve("${entry.outputPrefix}$suffix.png").toFile())
            }
        }
    }
}

fun retexture(output: Path, init: TextureGen.() -> Unit) {
    TextureGen(output).apply(init).generate()
}
