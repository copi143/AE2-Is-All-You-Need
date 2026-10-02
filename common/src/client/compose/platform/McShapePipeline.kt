package allyouneed.client.compose.platform

import allyouneed.util.logger
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

/**
 * 自有着色器/VAO/VBO 的纯 GL 多边形(线条)绘制管线,完全绕过 MC 的 RenderType/BufferBuilder
 * 实现:顶点在 CPU 侧用 GUI 位姿矩阵变换后,以 (x, y, argb) 三元组直传 GL,一次
 * glDrawArrays 画完一锅三角汤。与原版内容的绘制顺序通过先 [GuiGraphics.flush] 保住;
 * GL 状态按 [minecraftx.compose.text.msdf.MsdfRenderer] 的惯例保存/恢复。
 *
 * 混合模式与 RenderType.gui() 相同(半透明,straight alpha);无背面剔除,因此喂进来的
 * 三角形不需要绕序归一化。着色器编译失败时 [draw] 返回 false,调用方退回原版缓冲路径。
 */
internal object McShapePipeline {
    private var program = 0
    private var uProj = -1
    private var uModel = -1
    private var sdfProgram = 0
    private var sdfUProj = -1
    private var sdfUModel = -1
    private var sdfUHalfSize = -1
    private var sdfURadius = -1
    private var sdfUStrokeHalf = -1
    private var vao = 0
    private var vbo = 0
    private var sdfVao = 0
    private var sdfVbo = 0
    private var failed = false

    /** Transformed xy positions; colors are uploaded as explicit RGBA bytes. */
    private var verts = FloatArray(2 * 1024)

    /**
     * Draws [soup] immediately, CPU-transformed by the current GUI pose. Returns false when the
     * pipeline is unavailable (shader compile failure) and the caller should fall back to the
     * vanilla buffer path.
     */
    fun draw(graphics: GuiGraphics, soup: TriangleSoup): Boolean {
        if (failed) return false
        RenderSystem.assertOnRenderThread()
        if (program == 0 && !compile()) return false
        val vertexCount = soup.positions.size / 2
        if (vertexCount == 0) return true
        graphics.flush()

        val pose = graphics.pose().last().pose()
        ensureCap(vertexCount)
        var i = 0
        var v = 0
        while (v < vertexCount) {
            val x = soup.positions[i]
            val y = soup.positions[i + 1]
            verts[v * 2] = pose.m00() * x + pose.m10() * y + pose.m30()
            verts[v * 2 + 1] = pose.m01() * x + pose.m11() * y + pose.m31()
            v++
            i += 2
        }

        val prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
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
            val bytes = vertexCount * 12
            val native = MemoryUtil.memAlloc(bytes)
            try {
                for (vertex in 0 until vertexCount) {
                    native.putFloat(verts[vertex * 2])
                    native.putFloat(verts[vertex * 2 + 1])
                    native.putRgba(soup.colors[vertex])
                }
                native.flip()
                GL30.glBindVertexArray(vao)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, native, GL15.GL_STREAM_DRAW)
                GL20.glEnableVertexAttribArray(0)
                GL20.glEnableVertexAttribArray(1)
                GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 12, 0L)
                GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 12, 8L)
                GL11.glDisable(GL11.GL_DEPTH_TEST)
                GL11.glDisable(GL11.GL_CULL_FACE)
                GL11.glEnable(GL11.GL_BLEND)
                GL14.glBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                )
                GL20.glUseProgram(program)
                MemoryStack.stackPush().use { stack ->
                    val buf = stack.mallocFloat(16)
                    GL20.glUniformMatrix4fv(uModel, false, RenderSystem.getModelViewMatrix().get(buf))
                    buf.clear()
                    GL20.glUniformMatrix4fv(uProj, false, RenderSystem.getProjectionMatrix().get(buf))
                }
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount)
            } finally {
                MemoryUtil.memFree(native)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
                GL30.glBindVertexArray(prevVao)
            }
        } finally {
            GL20.glUseProgram(prevProgram)
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA)
        }
        return true
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
        if (sdfVbo != 0) {
            GL15.glDeleteBuffers(sdfVbo)
            sdfVbo = 0
        }
        if (sdfVao != 0) {
            GL30.glDeleteVertexArrays(sdfVao)
            sdfVao = 0
        }
        if (program != 0) {
            GL20.glDeleteProgram(program)
            program = 0
        }
        if (sdfProgram != 0) {
            GL20.glDeleteProgram(sdfProgram)
            sdfProgram = 0
        }
    }

    /**
     * SDF 解析式圆角矩形/圆(半径 [rx]=[ry] 时):fragment 里按符号距离 + fwidth 求屏幕空间
     * 覆盖率,边缘质量优于羽化三角带,且在任意缩放/旋转下 AA 宽度自适应。[strokeHalf] > 0
     * 画描边环,否则填充。管线不可用时返回 false,调用方退回细分+羽化路径。
     */
    fun roundRect(
        graphics: GuiGraphics,
        left: Float, top: Float, right: Float, bottom: Float,
        radius: Float, color: Int, strokeHalf: Float,
    ): Boolean {
        if (failed) return false
        RenderSystem.assertOnRenderThread()
        if (sdfProgram == 0 && !compileSdf()) return false
        val hw = (right - left) / 2f
        val hh = (bottom - top) / 2f
        if (hw <= 0f || hh <= 0f) return true
        graphics.flush()

        val pose = graphics.pose().last().pose()
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        // pos.xy (pose-transformed) + local.xy; the RGBA attribute is appended during upload.
        val data = FloatArray(6 * 4)
        val xs = floatArrayOf(left, right, right, left, right, left)
        val ys = floatArrayOf(top, top, bottom, top, bottom, bottom)
        val lx = floatArrayOf(-hw, hw, hw, -hw, hw, -hw)
        val ly = floatArrayOf(-hh, -hh, hh, -hh, hh, hh)
        for (v in 0 until 6) {
            data[v * 4] = pose.m00() * xs[v] + pose.m10() * ys[v] + pose.m30()
            data[v * 4 + 1] = pose.m01() * xs[v] + pose.m11() * ys[v] + pose.m31()
            data[v * 4 + 2] = lx[v]
            data[v * 4 + 3] = ly[v]
        }

        val prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val prevBlend = GL11.glIsEnabled(GL11.GL_BLEND)
        val prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            if (sdfVao == 0) sdfVao = GL30.glGenVertexArrays()
            if (sdfVbo == 0) sdfVbo = GL15.glGenBuffers()
            val native = MemoryUtil.memAlloc(6 * 20)
            try {
                for (vertex in 0 until 6) {
                    for (component in 0..3) native.putFloat(data[vertex * 4 + component])
                    native.putRgba(color)
                }
                native.flip()
                GL30.glBindVertexArray(sdfVao)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, sdfVbo)
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, native, GL15.GL_STREAM_DRAW)
                GL20.glEnableVertexAttribArray(0)
                GL20.glEnableVertexAttribArray(1)
                GL20.glEnableVertexAttribArray(2)
                GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 20, 0L)
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 20, 8L)
                GL20.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, 20, 16L)
                GL11.glDisable(GL11.GL_DEPTH_TEST)
                GL11.glDisable(GL11.GL_CULL_FACE)
                GL11.glEnable(GL11.GL_BLEND)
                GL14.glBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                )
                GL20.glUseProgram(sdfProgram)
                MemoryStack.stackPush().use { stack ->
                    val buf = stack.mallocFloat(16)
                    GL20.glUniformMatrix4fv(sdfUModel, false, RenderSystem.getModelViewMatrix().get(buf))
                    buf.clear()
                    GL20.glUniformMatrix4fv(sdfUProj, false, RenderSystem.getProjectionMatrix().get(buf))
                }
                GL20.glUniform2f(sdfUHalfSize, hw, hh)
                GL20.glUniform1f(sdfURadius, minOf(radius, hw, hh))
                GL20.glUniform1f(sdfUStrokeHalf, strokeHalf)
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6)
            } finally {
                MemoryUtil.memFree(native)
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
                GL30.glBindVertexArray(prevVao)
            }
        } finally {
            GL20.glUseProgram(prevProgram)
            if (prevBlend) GL11.glEnable(GL11.GL_BLEND) else GL11.glDisable(GL11.GL_BLEND)
            if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST) else GL11.glDisable(GL11.GL_DEPTH_TEST)
            if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE) else GL11.glDisable(GL11.GL_CULL_FACE)
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA)
        }
        return true
    }

    private fun ensureCap(vertices: Int) {
        val need = vertices * 2
        if (need <= verts.size) return
        var cap = verts.size
        while (cap < need) cap *= 2
        verts = verts.copyOf(cap)
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
        GL20.glBindAttribLocation(prog, 1, "Color")
        GL20.glLinkProgram(prog)
        GL20.glDeleteShader(vs)
        GL20.glDeleteShader(fs)
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            logger.error("shape pipeline link failed: {}", GL20.glGetProgramInfoLog(prog))
            GL20.glDeleteProgram(prog)
            failed = true
            return false
        }
        program = prog
        uProj = GL20.glGetUniformLocation(prog, "ProjMat")
        uModel = GL20.glGetUniformLocation(prog, "ModelViewMat")
        return true
    }

    private fun compileShader(type: Int, src: String): Int {
        val id = GL20.glCreateShader(type)
        GL20.glShaderSource(id, src)
        GL20.glCompileShader(id)
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            logger.error("shape shader compile failed: {}", GL20.glGetShaderInfoLog(id))
            GL20.glDeleteShader(id)
            return 0
        }
        return id
    }

    private fun compileSdf(): Boolean {
        val vs = compileShader(GL20.GL_VERTEX_SHADER, SDF_VERT)
        val fs = compileShader(GL20.GL_FRAGMENT_SHADER, SDF_FRAG)
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
        GL20.glBindAttribLocation(prog, 1, "Local")
        GL20.glBindAttribLocation(prog, 2, "Color")
        GL20.glLinkProgram(prog)
        GL20.glDeleteShader(vs)
        GL20.glDeleteShader(fs)
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            logger.error("sdf program link failed: {}", GL20.glGetProgramInfoLog(prog))
            GL20.glDeleteProgram(prog)
            failed = true
            return false
        }
        sdfProgram = prog
        sdfUProj = GL20.glGetUniformLocation(prog, "ProjMat")
        sdfUModel = GL20.glGetUniformLocation(prog, "ModelViewMat")
        sdfUHalfSize = GL20.glGetUniformLocation(prog, "HalfSize")
        sdfURadius = GL20.glGetUniformLocation(prog, "Radius")
        sdfUStrokeHalf = GL20.glGetUniformLocation(prog, "StrokeHalf")
        return true
    }

    private const val VERT = """#version 330 core
in vec2 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 0.0, 1.0);
    vColor = Color;
}
"""

    private const val FRAG = """#version 330 core
in vec4 vColor;
out vec4 fragColor;
void main() {
    fragColor = vColor;
}
"""

    private const val SDF_VERT = """#version 330 core
in vec2 Position;
in vec2 Local;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 vLocal;
out vec4 vColor;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 0.0, 1.0);
    vLocal = Local;
    vColor = Color;
}
"""

    private const val SDF_FRAG = """#version 330 core
in vec2 vLocal;
in vec4 vColor;
uniform vec2 HalfSize;
uniform float Radius;
uniform float StrokeHalf;
out vec4 fragColor;
float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}
void main() {
    float d = sdRoundBox(vLocal, HalfSize, Radius);
    float aa = max(fwidth(d) * 0.75, 1.0e-4);
    float cov = StrokeHalf > 0.0
        ? 1.0 - smoothstep(-aa, aa, abs(d) - StrokeHalf)
        : 1.0 - smoothstep(-aa, aa, d);
    if (cov <= 0.0) discard;
    fragColor = vec4(vColor.rgb, vColor.a * cov);
}
"""
}
