package minecraftx.text

import allyouneed.client.msdftext.MsdfGenerator
import allyouneed.client.msdftext.SystemFonts
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.stb.STBImageWrite
import org.lwjgl.system.MemoryUtil
import java.io.File

/**
 * 像素级诊断:在真 GL 上下文里用渲染器同款 shader/状态画一个字形,
 * 读回像素存 PNG,肉眼判定边缘质量。
 */
class MsdfGlRenderTest {

    private fun compileShader(type: Int, src: String): Int {
        val id = glCreateShader(type)
        glShaderSource(id, src)
        glCompileShader(id)
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            throw IllegalStateException("shader failed: " + glGetShaderInfoLog(id))
        }
        return id
    }

    private fun program(frag: String): Int {
        val vs = compileShader(GL_VERTEX_SHADER, VERT)
        val fs = compileShader(GL_FRAGMENT_SHADER, frag)
        val prog = glCreateProgram()
        glAttachShader(prog, vs)
        glAttachShader(prog, fs)
        glBindAttribLocation(prog, 0, "Position")
        glBindAttribLocation(prog, 1, "UV0")
        glBindAttribLocation(prog, 2, "Color")
        glLinkProgram(prog)
        glDeleteShader(vs)
        glDeleteShader(fs)
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            throw IllegalStateException("link failed: " + glGetProgramInfoLog(prog))
        }
        return prog
    }

    private fun ortho(left: Float, right: Float, bottom: Float, top: Float): FloatArray =
        floatArrayOf(
            2f / (right - left), 0f, 0f, 0f,
            0f, 2f / (top - bottom), 0f, 0f,
            0f, 0f, -1f, 0f,
            -(right + left) / (right - left), -(top + bottom) / (top - bottom), 0f, 1f,
        )

    private fun draw(prog: Int, tex: Int, w: Int, h: Int, quad: FloatArray, pxRange: Float, atlasSize: Float) {
        val vao = glGenVertexArrays()
        val vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val buf = MemoryUtil.memAlloc(quad.size * 4)
        buf.asFloatBuffer().put(quad)
        buf.limit(quad.size * 4)
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
        MemoryUtil.memFree(buf)
        val stride = 9 * 4
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glEnableVertexAttribArray(2)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 12L)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 20L)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glEnable(GL_BLEND)
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glUseProgram(prog)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, tex)
        glUniform1i(glGetUniformLocation(prog, "Sampler0"), 0)
        glUniform1f(glGetUniformLocation(prog, "PxRange"), pxRange)
        val uAtlas = glGetUniformLocation(prog, "AtlasSize")
        if (uAtlas >= 0) glUniform2f(uAtlas, atlasSize, atlasSize)
        glUniform1f(glGetUniformLocation(prog, "Weight"), 0f)
        glUniformMatrix4fv(glGetUniformLocation(prog, "ModelViewMat"), false, FloatArray(16).also {
            it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f
        })
        glUniformMatrix4fv(glGetUniformLocation(prog, "ProjMat"), false, ortho(0f, 400f, 0f, 400f))
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        glUseProgram(0)
    }

    /** 左斜边直线度:逐行找 alpha 过半点的 x,拟合直线,最大残差即边缘抖动。hint 台阶约 3~6px,正常应 < 2px。 */
    private fun edgeWobble(buf: java.nio.ByteBuffer, w: Int, h: Int, y0: Int, y1: Int): Double {
        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        for (sy in y0 until y1) {
            var found = -1
            for (sx in 0 until w / 2) {
                // 预乘混合后 alpha 恒 1,用 R 通道(背景 38,字 255,中点 146)找边
                val r = buf.get((sy * w + sx) * 4).toInt() and 0xFF
                if (r > 146) {
                    found = sx
                    break
                }
            }
            if (found >= 0) {
                xs.add(found.toDouble())
                ys.add(sy.toDouble())
            }
        }
        if (xs.size < 8) return Double.MAX_VALUE
        // 稳健拟合:先拟合一次,去掉大残差野点(如尖顶行)再拟合,避免野点带偏直线
        var keep = (0 until xs.size).toList()
        var k = 0.0
        var b = 0.0
        repeat(2) {
            val n = keep.size
            if (n < 8) return Double.MAX_VALUE
            val mx = keep.sumOf { xs[it] } / n
            val my = keep.sumOf { ys[it] } / n
            var sxy = 0.0
            var sxx = 0.0
            for (i in keep) {
                sxy += (xs[i] - mx) * (ys[i] - my)
                sxx += (xs[i] - mx) * (xs[i] - mx)
            }
            if (sxx < 1e-9) return Double.MAX_VALUE
            k = sxy / sxx
            b = my - k * mx
            keep = keep.filter { kotlin.math.abs(ys[it] - (k * xs[it] + b)) <= 4.0 }
        }
        val n = keep.size
        if (n < 8) return Double.MAX_VALUE
        // 90 分位:只度量周期性齿高
        val sorted = keep.map { kotlin.math.abs(ys[it] - (k * xs[it] + b)) }.sorted()
        return sorted[(n * 0.9).toInt().coerceIn(0, n - 1)]
    }

    private fun readPixels(w: Int, h: Int): java.nio.ByteBuffer {
        val buf = MemoryUtil.memAlloc(w * h * 4)
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf)
        return buf
    }

    private fun save(name: String, w: Int, h: Int) {
        val buf = readPixels(w, h)
        // flip rows (GL bottom-up -> PNG top-down)
        val flipped = MemoryUtil.memAlloc(w * h * 4)
        val row = ByteArray(w * 4)
        for (y in 0 until h) {
            buf.position((h - 1 - y) * w * 4)
            buf.get(row)
            flipped.put(row)
        }
        flipped.flip()
        MemoryUtil.memFree(buf)
        val dir = File("build/msdf-diag").also { it.mkdirs() }
        val ok = STBImageWrite.stbi_write_png(File(dir, name).absolutePath, w, h, 4, flipped, w * 4)
        MemoryUtil.memFree(flipped)
        println("DIAG png $name ok=$ok")
    }

    @Test
    fun `render glyph magnified`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(glfwInit(), "no display for GL diagnostics")
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val win = glfwCreateWindow(400, 400, "msdf-diag", 0, 0)
        if (win == 0L) throw IllegalStateException("no window")
        glfwMakeContextCurrent(win)
        GL.createCapabilities()
        println("DIAG gl=" + glGetString(GL_VERSION))

        val chain = SystemFonts.resolve(12f)
        val bmp = MsdfGenerator.generate(chain.genFace('A'.code), 'A'.code)!!
        val bw = bmp.width
        val bh = bmp.height

        fun upload(filter: Int): Int {
            val tex = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, tex)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            val buf = MemoryUtil.memAlloc(bmp.pixels.size)
            buf.put(bmp.pixels).flip()
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bw, bh, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
            MemoryUtil.memFree(buf)
            return tex
        }

        // quad: bitmap magnified ~5.7x like the 256px case, bottom-left origin
        val mag = 256f / bh
        val qw = bw * mag
        val qh = 256f
        val qx = (400f - qw) / 2f
        val qy = (400f - qh) / 2f
        fun quad(): FloatArray {
            // two triangles, pos(x,y,0) uv color(1,1,1,1)
            val v = floatArrayOf(
                qx, qy, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
                qx, qy + qh, 0f, 0f, 1f, 1f, 1f, 1f, 1f,
                qx + qw, qy + qh, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
                qx, qy, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
                qx + qw, qy + qh, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
                qx + qw, qy, 0f, 1f, 0f, 1f, 1f, 1f, 1f,
            )
            return v
        }

        val progNew = program(FRAG_NEW)
        val texLin = upload(GL_LINEAR)
        run {
            // 回读验证:GPU 上的纹理必须和 CPU 位图一致
            glBindTexture(GL_TEXTURE_2D, texLin)
            glPixelStorei(GL_PACK_ALIGNMENT, 1)
            val rb = MemoryUtil.memAlloc(bw * bh * 4)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, rb)
            var diff = 0
            var maxD = 0
            for (i in bmp.pixels.indices) {
                val d = kotlin.math.abs((rb.get(i).toInt() and 0xFF) - (bmp.pixels[i].toInt() and 0xFF))
                if (d != 0) {
                    diff++
                    if (d > maxD) maxD = d
                }
            }
            MemoryUtil.memFree(rb)
            // 读第一行中值,看 GPU 场有没有渐变
            println("DIAG texread diffBytes=$diff/${bmp.pixels.size} maxDiff=$maxD")
        }
        glClearColor(0.15f, 0.15f, 0.15f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        draw(progNew, texLin, 400, 400, quad(), 8f, maxOf(bw, bh).toFloat())
        // 只测左斜边直线段:避开顶部尖角(拐角非直线)和横梁带
        val wobbleBuf = readPixels(400, 400)
        val wobble = edgeWobble(wobbleBuf, 400, 400, 215, 290)
        MemoryUtil.memFree(wobbleBuf)
        println("DIAG edge wobble(p90)=${"%.2f".format(wobble)}px")
        save("msdf-linear-256.png", 400, 400)

        val texNear = upload(GL_NEAREST)
        glClear(GL_COLOR_BUFFER_BIT)
        draw(progNew, texNear, 400, 400, quad(), 8f, maxOf(bw, bh).toFloat())
        save("msdf-nearest-256.png", 400, 400)

        val progOld = program(FRAG_OLD)
        glClear(GL_COLOR_BUFFER_BIT)
        draw(progOld, texLin, 400, 400, quad(), 8f, maxOf(bw, bh).toFloat())
        save("msdf-oldshader-linear-256.png", 400, 400)

        val progDbg = program(FRAG_DBG_SD)
        glClear(GL_COLOR_BUFFER_BIT)
        draw(progDbg, texLin, 400, 400, quad(), 8f, maxOf(bw, bh).toFloat())
        save("msdf-dbg-sd.png", 400, 400)

        val progRng = program(FRAG_DBG_RANGE)
        glClear(GL_COLOR_BUFFER_BIT)
        draw(progRng, texLin, 400, 400, quad(), 8f, maxOf(bw, bh).toFloat())
        save("msdf-dbg-range.png", 400, 400)

        org.junit.jupiter.api.Assertions.assertTrue(wobble < 3.0, "diagonal edge wobbles ${wobble}px, hinting steps suspected")

        glfwDestroyWindow(win)
        glfwTerminate()
    }

    @Test
    fun `dump genPx variants`() {
        for (genPx in listOf(48f, 64f, 96f)) {
            val chain = SystemFonts.resolve(12f, genPx)
            val bmp = MsdfGenerator.generate(chain.genFace('中'.code), '中'.code, 8f)!!
            val w = bmp.width; val h = bmp.height
            val buf = MemoryUtil.memAlloc(w*h*4)
            for (y in 0 until h) for (x in 0 until w) {
                val i = (y*w+x)*4
                val r = bmp.pixels[i].toInt() and 0xFF
                val g = bmp.pixels[i+1].toInt() and 0xFF
                val b = bmp.pixels[i+2].toInt() and 0xFF
                val m = maxOf(minOf(r,g), minOf(maxOf(r,g), b))
                val v = m.toByte()
                buf.put(i, v); buf.put(i+1, v); buf.put(i+2, v); buf.put(i+3, 0xFF.toByte())
            }
            val dir = File("build/msdf-diag").also { it.mkdirs() }
            STBImageWrite.stbi_write_png(File(dir, "zhong-gen${genPx.toInt()}-median.png").absolutePath, w,h,4,buf,w*4)
            MemoryUtil.memFree(buf)
            println("DIAG genPx $genPx -> ${w}x${h}")
        }
    }

    @Test
    fun `render intersecting string via atlas`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(glfwInit(), "no display for GL diagnostics")
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val win = glfwCreateWindow(800, 200, "msdf-atlas", 0, 0)
        if (win == 0L) throw IllegalStateException("no window")
        glfwMakeContextCurrent(win)
        GL.createCapabilities()
        val chain = SystemFonts.resolve(12f)
        val atlas = allyouneed.client.msdftext.GlyphAtlas(512, 2048, 1)
        val text = "中文器藏AX"
        val slots = mutableListOf<allyouneed.client.msdftext.AtlasSlot>()
        for (cp in text.codePoints().toArray()) {
            val bmp = MsdfGenerator.generate(chain.genFace(cp), cp, 8f)!!
            val slot = atlas.pack(allyouneed.client.msdftext.GlyphKey(chain.faceFor(cp).family, cp), bmp)!!
            slots.add(slot)
        }
        atlas.ensureTexture()
        val tex = atlas.textureId
        // verify no GL error on atlas upload
        println("DIAG atlas size=${atlas.size} slots=${slots.size}")
        // Build quads for high mag (5x)
        val mag = 5f
        val prog = program(FRAG_NEW)
        glClearColor(0.15f, 0.15f, 0.15f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        // Batch draw all glyphs
        val verts = mutableListOf<Float>()
        var pen = 20f
        val baseline = 60f
        for (slot in slots) {
            val w = slot.width * mag
            val h = slot.height * mag
            // Atlas UVs
            val u0 = slot.x / atlas.size.toFloat()
            val v0 = slot.y / atlas.size.toFloat()
            val u1 = (slot.x + slot.width) / atlas.size.toFloat()
            val v1 = (slot.y + slot.height) / atlas.size.toFloat()
            // Quad positions: place bitmap origin at pen,baseline (simplified, ignores originX/Y)
            // Use same logic as MsdfTextEngine: x0=pen+originX*mag, y0=baseline+originY*mag
            val x0 = pen + slot.originX * mag * 0.25f // toDraw=0.25
            val y0 = baseline + slot.originY * mag * 0.25f
            val x1 = x0 + w * 0.25f
            val y1 = y0 + h * 0.25f
            // Two triangles
            fun put(ax: Float, ay: Float, u: Float, v: Float) {
                verts.add(ax); verts.add(ay); verts.add(0f); verts.add(u); verts.add(v); verts.add(1f); verts.add(1f); verts.add(1f); verts.add(1f)
            }
            put(x0, y0, u0, v0)
            put(x0, y1, u0, v1)
            put(x1, y1, u1, v1)
            put(x0, y0, u0, v0)
            put(x1, y1, u1, v1)
            put(x1, y0, u1, v0)
            pen += (slot.width * 0.25f * mag) + 10f
        }
        val vao = glGenVertexArrays()
        val vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val buf = MemoryUtil.memAlloc(verts.size * 4)
        buf.asFloatBuffer().put(verts.toFloatArray()); buf.limit(verts.size*4)
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW)
        MemoryUtil.memFree(buf)
        val stride = 9*4
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L)
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 12L)
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 20L)
        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glEnable(GL_BLEND)
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
        glUseProgram(prog)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        // Use our sampler path
        val samp = org.lwjgl.opengl.GL33.glGenSamplers()
        org.lwjgl.opengl.GL33.glSamplerParameteri(samp, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        org.lwjgl.opengl.GL33.glSamplerParameteri(samp, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        org.lwjgl.opengl.GL33.glBindSampler(0, samp)
        glBindTexture(GL_TEXTURE_2D, tex)
        glUniform1i(glGetUniformLocation(prog, "Sampler0"), 0)
        glUniform1f(glGetUniformLocation(prog, "PxRange"), 8f)
        glUniform2f(glGetUniformLocation(prog, "AtlasSize"), atlas.size.toFloat(), atlas.size.toFloat())
        glUniform1f(glGetUniformLocation(prog, "Weight"), 0f)
        val proj = floatArrayOf(2f/800f,0f,0f,0f, 0f,2f/200f,0f,0f, 0f,0f,-1f,0f, -1f,-1f,0f,1f)
        // Actually use ortho 0..800,0..200
        val ortho = floatArrayOf(2f/800f,0f,0f,0f, 0f,2f/200f,0f,0f, 0f,0f,-1f,0f, -1f,-1f,0f,1f)
        glUniformMatrix4fv(glGetUniformLocation(prog, "ProjMat"), false, ortho)
        glUniformMatrix4fv(glGetUniformLocation(prog, "ModelViewMat"), false, floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f))
        glDrawArrays(GL_TRIANGLES, 0, verts.size/9)
        save("msdf-atlas-string.png", 800, 200)
        glDeleteBuffers(vbo); glDeleteVertexArrays(vao); org.lwjgl.opengl.GL33.glDeleteSamplers(samp)
        glUseProgram(0)
        glfwDestroyWindow(win)
        glfwTerminate()
    }

    private companion object {
        const val VERT = """#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 vUv;
out vec4 vColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUv = UV0;
    vColor = Color;
}
"""
        const val FRAG_NEW = """#version 150
uniform sampler2D Sampler0;
uniform float PxRange;
uniform vec2 AtlasSize;
uniform float Weight;
in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;
float median3(vec3 p) {
    return max(min(p.r, p.g), min(max(p.r, p.g), p.b));
}
float screenPxRange() {
    vec2 atlas = max(AtlasSize, vec2(textureSize(Sampler0, 0)));
    vec2 unitRange = vec2(PxRange) / max(atlas, vec2(1.0));
    vec2 fw = max(fwidth(vUv), vec2(1.0e-8));
    vec2 screenTexSize = vec2(1.0) / fw;
    return clamp(0.5 * dot(unitRange, screenTexSize), 1.0, 32.0);
}
void main() {
    vec3 msd = texture(Sampler0, vUv).rgb;
    float sd = median3(msd);
    float opa = clamp(screenPxRange() * (sd - 0.5 + Weight) + 0.5, 0.0, 1.0) * vColor.a;
    fragColor = vec4(vColor.rgb * opa, opa);
}
"""
        const val FRAG_DBG_SD = """#version 150
uniform sampler2D Sampler0;
uniform float PxRange;
uniform vec2 AtlasSize;
uniform float Weight;
in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;
float median3(vec3 p) {
    return max(min(p.r, p.g), min(max(p.r, p.g), p.b));
}
void main() {
    vec3 msd = texture(Sampler0, vUv).rgb;
    float sd = median3(msd);
    fragColor = vec4(vec3(sd), 1.0);
}
"""
        const val FRAG_DBG_RANGE = """#version 150
uniform sampler2D Sampler0;
uniform float PxRange;
uniform vec2 AtlasSize;
uniform float Weight;
in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;
float screenPxRange() {
    vec2 atlas = max(AtlasSize, vec2(textureSize(Sampler0, 0)));
    vec2 unitRange = vec2(PxRange) / max(atlas, vec2(1.0));
    vec2 fw = max(fwidth(vUv), vec2(1.0e-8));
    vec2 screenTexSize = vec2(1.0) / fw;
    return clamp(0.5 * dot(unitRange, screenTexSize), 1.0, 32.0);
}
void main() {
    fragColor = vec4(vec3(screenPxRange() / 45.0), 1.0);
}
"""
        const val FRAG_OLD = """#version 150
uniform sampler2D Sampler0;
uniform float PxRange;
uniform vec2 AtlasSize;
uniform float Weight;
in vec2 vUv;
in vec4 vColor;
out vec4 fragColor;
float median3(vec3 p) {
    return max(min(p.r, p.g), min(max(p.r, p.g), p.b));
}
void main() {
    vec3 msd = texture(Sampler0, vUv).rgb;
    float sd = median3(msd);
    float opa = clamp(PxRange * (sd - 0.5 + Weight) + 0.5, 0.0, 1.0) * vColor.a;
    fragColor = vec4(vColor.rgb * opa, opa);
}
"""
    }
}
