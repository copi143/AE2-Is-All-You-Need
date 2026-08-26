package minecraftx.compose.text.msdf

import allyouneed.util.logger
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

internal class MsdfRenderer(private val atlas: GlyphAtlas) {
    private var program = 0
    private var uProj = -1
    private var uModel = -1
    private var uSampler = -1
    private var uPxRange = -1
    private var uWeight = -1
    private var vbo = 0
    private var vao = 0
    private var failed = false
    private var verts = FloatArray(FLOATS_PER_GLYPH * 64)
    private var glyphCount = 0
    private var batchWeight = 0f
    private var gfx: GuiGraphics? = null
    private var batchPxRange = 4f

    fun ready(): Boolean {
        if (failed) return false
        if (program != 0) return true
        return compile()
    }

    fun begin(graphics: GuiGraphics, pxRange: Float) {
        gfx = graphics
        batchPxRange = pxRange
        glyphCount = 0
        batchWeight = 0f
        atlas.ensureTexture()
    }

    fun quad(
        x0: Float, y0: Float, x1: Float, y1: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float, g: Float, b: Float, a: Float,
        shear: Float,
        weight: Float,
    ) {
        if (weight != batchWeight && glyphCount > 0) flush()
        batchWeight = weight
        ensureCap(glyphCount + 1)
        val i = glyphCount * FLOATS_PER_GLYPH
        put(i + 0, x0 + shear, y0, u0, v0, r, g, b, a)
        put(i + 9, x0, y1, u0, v1, r, g, b, a)
        put(i + 18, x1, y1, u1, v1, r, g, b, a)
        put(i + 27, x0 + shear, y0, u0, v0, r, g, b, a)
        put(i + 36, x1, y1, u1, v1, r, g, b, a)
        put(i + 45, x1 + shear, y0, u1, v0, r, g, b, a)
        glyphCount++
    }

    fun flush() {
        if (glyphCount == 0 || program == 0) {
            glyphCount = 0
            return
        }
        val g = gfx ?: run {
            glyphCount = 0
            return
        }
        applyPose(g.pose().last().pose())
        val prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val prevBlend = GL11.glIsEnabled(GL11.GL_BLEND)
        val prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            if (vao == 0) vao = GL30.glGenVertexArrays()
            if (vbo == 0) vbo = GL15.glGenBuffers()
            val bytes = glyphCount * FLOATS_PER_GLYPH * 4
            val native = MemoryUtil.memAlloc(bytes)
            try {
                native.asFloatBuffer().put(verts, 0, glyphCount * FLOATS_PER_GLYPH)
                native.limit(bytes)
                GL30.glBindVertexArray(vao)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, native, GL15.GL_STREAM_DRAW)
                val stride = 9 * 4
                GL20.glEnableVertexAttribArray(0)
                GL20.glEnableVertexAttribArray(1)
                GL20.glEnableVertexAttribArray(2)
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L)
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 12L)
                GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, stride, 20L)
                GL11.glDisable(GL11.GL_DEPTH_TEST)
                GL11.glDisable(GL11.GL_CULL_FACE)
                GL11.glEnable(GL11.GL_BLEND)
                GL14.glBlendFuncSeparate(
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                )
                GL20.glUseProgram(program)
                GL13.glActiveTexture(GL13.GL_TEXTURE0)
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.textureId)
                GL20.glUniform1i(uSampler, 0)
                GL20.glUniform1f(uPxRange, batchPxRange)
                GL20.glUniform1f(uWeight, batchWeight)
                MemoryStack.stackPush().use { stack ->
                    val buf = stack.mallocFloat(16)
                    GL20.glUniformMatrix4fv(uModel, false, RenderSystem.getModelViewMatrix().get(buf))
                    buf.clear()
                    GL20.glUniformMatrix4fv(uProj, false, RenderSystem.getProjectionMatrix().get(buf))
                }
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, glyphCount * 6)
            } finally {
                MemoryUtil.memFree(native)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
                GL30.glBindVertexArray(prevVao)
            }
        } finally {
            GL20.glUseProgram(prevProgram)
            GL13.glActiveTexture(prevActive)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex)
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA)
            glyphCount = 0
        }
    }

    fun destroy() {
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo)
            vbo = 0
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao)
            vao = 0
        }
        if (program != 0) {
            GL20.glDeleteProgram(program)
            program = 0
        }
        atlas.destroy()
    }

    private fun compile(): Boolean {
        val vs = compileShader(GL20.GL_VERTEX_SHADER, VERT)
        val fs = compileShader(GL20.GL_FRAGMENT_SHADER, FRAG)
        if (vs == 0 || fs == 0) {
            if (vs != 0) GL20.glDeleteShader(vs)
            if (fs != 0) GL20.glDeleteShader(fs)
            failed = true
            return false
        }
        val prog = GL20.glCreateProgram()
        GL20.glAttachShader(prog, vs)
        GL20.glAttachShader(prog, fs)
        GL20.glBindAttribLocation(prog, 0, "Position")
        GL20.glBindAttribLocation(prog, 1, "UV0")
        GL20.glBindAttribLocation(prog, 2, "Color")
        GL20.glLinkProgram(prog)
        GL20.glDeleteShader(vs)
        GL20.glDeleteShader(fs)
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            logger.error("MSDF program link failed: {}", GL20.glGetProgramInfoLog(prog))
            GL20.glDeleteProgram(prog)
            failed = true
            return false
        }
        program = prog
        uProj = GL20.glGetUniformLocation(prog, "ProjMat")
        uModel = GL20.glGetUniformLocation(prog, "ModelViewMat")
        uSampler = GL20.glGetUniformLocation(prog, "Sampler0")
        uPxRange = GL20.glGetUniformLocation(prog, "PxRange")
        uWeight = GL20.glGetUniformLocation(prog, "Weight")
        return true
    }

    private fun compileShader(type: Int, src: String): Int {
        val id = GL20.glCreateShader(type)
        GL20.glShaderSource(id, src)
        GL20.glCompileShader(id)
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            logger.error("MSDF shader compile failed: {}", GL20.glGetShaderInfoLog(id))
            GL20.glDeleteShader(id)
            return 0
        }
        return id
    }

    private fun applyPose(pose: org.joml.Matrix4f) {
        val n = glyphCount * 6
        var i = 0
        repeat(n) {
            val x = verts[i]
            val y = verts[i + 1]
            verts[i] = pose.m00() * x + pose.m10() * y + pose.m30()
            verts[i + 1] = pose.m01() * x + pose.m11() * y + pose.m31()
            i += 9
        }
    }

    private fun ensureCap(glyphs: Int) {
        val need = glyphs * FLOATS_PER_GLYPH
        if (need <= verts.size) return
        var cap = verts.size
        while (cap < need) cap *= 2
        verts = verts.copyOf(cap)
    }

    private fun put(i: Int, x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float) {
        verts[i] = x
        verts[i + 1] = y
        verts[i + 2] = 0f
        verts[i + 3] = u
        verts[i + 4] = v
        verts[i + 5] = r
        verts[i + 6] = g
        verts[i + 7] = b
        verts[i + 8] = a
    }

    private companion object {
        const val FLOATS_PER_GLYPH = 6 * 9

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

        const val FRAG = """#version 150
uniform sampler2D Sampler0;
uniform float PxRange;
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
    if (opa < 0.004) discard;
    fragColor = vec4(vColor.rgb * opa, opa);
}
"""
    }
}
