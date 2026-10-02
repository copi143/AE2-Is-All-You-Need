package allyouneed.client.compose.platform

import org.joml.Matrix4f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

/**
 * Independent framebuffer for a path clip. The backdrop is copied before drawing; composition
 * mixes the finished pixels with that snapshot using the mask coverage. This preserves ordinary
 * Minecraft blending (including translucent items/text), without assuming premultiplied alpha.
 * Targets are pooled by nesting depth and released when an owner closes. No MC attachment is changed.
 */
internal object McClipTarget {
    private data class Target(val width: Int, val height: Int, val depthFormat: Int) {
        val framebuffer = glGenFramebuffers()
        val color = glGenTextures()
        val backdrop = glGenTextures()
        val depth = if (depthFormat != 0) glGenRenderbuffers() else 0

        fun initialize() {
            for (texture in listOf(color, backdrop)) {
                glBindTexture(GL_TEXTURE_2D, texture)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer)
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0)
            if (depth != 0) {
                glBindRenderbuffer(GL_RENDERBUFFER, depth)
                glRenderbufferStorage(GL_RENDERBUFFER, depthFormat, width, height)
                glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth)
            }
            check(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Incomplete Compose clip target" }
        }

        fun destroy() {
            glDeleteFramebuffers(framebuffer)
            glDeleteTextures(color)
            glDeleteTextures(backdrop)
            if (depth != 0) glDeleteRenderbuffers(depth)
        }
    }

    private val pool = ArrayDeque<Target>()
    private var program = 0
    private var vao = 0
    private var vbo = 0

    /** Actual GL state is restored, including bindings cached independently by Minecraft. */
    private class State {
        val draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
        val read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
        val renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING)
        val program = glGetInteger(GL_CURRENT_PROGRAM)
        val vao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val buffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val activeTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        val textures = IntArray(2) { unit -> glActiveTexture(GL_TEXTURE0 + unit); glGetInteger(GL_TEXTURE_BINDING_2D) }
        val blend = glIsEnabled(GL_BLEND)
        val depth = glIsEnabled(GL_DEPTH_TEST)
        val stencil = glIsEnabled(GL_STENCIL_TEST)
        val cull = glIsEnabled(GL_CULL_FACE)
        val scissor = glIsEnabled(GL_SCISSOR_TEST)
        val colorMask = BooleanArray(4).also { mask ->
            val values = org.lwjgl.system.MemoryStack.stackPush()
            try {
                val bytes = values.malloc(4)
                glGetBooleanv(GL_COLOR_WRITEMASK, bytes)
                for (i in 0..3) mask[i] = bytes[i].toInt() != 0
            } finally { values.close() }
        }

        fun restore() {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read)
            glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer)
            glUseProgram(program)
            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, buffer)
            for (unit in 0..1) { glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, textures[unit]) }
            glActiveTexture(activeTexture)
            for ((cap, enabled) in listOf(GL_BLEND to blend, GL_DEPTH_TEST to depth, GL_STENCIL_TEST to stencil,
                GL_CULL_FACE to cull, GL_SCISSOR_TEST to scissor)) {
                if (enabled) glEnable(cap) else glDisable(cap)
            }
            glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3])
        }
    }

    class Layer internal constructor(private val finishBlock: () -> Unit) {
        private var finished = false
        fun finish() {
            if (finished) return
            finished = true
            finishBlock()
        }
    }

    fun begin(mask: TriangleSoup, projection: Matrix4f, difference: Boolean): Layer {
        val state = State()
        val viewport = IntArray(4).also { glGetIntegerv(GL_VIEWPORT, it) }
        val width = viewport[0] + viewport[2]
        val height = viewport[1] + viewport[3]
        var target: Target? = null
        try {
            ensureProgram()
            val format = depthFormat(state.draw)
            // A resize discards stale pooled surfaces; active parent surfaces remain untouched.
            while (pool.isNotEmpty()) {
                val candidate = pool.removeLast()
                if (candidate.width == width && candidate.height == height && candidate.depthFormat == format) {
                    target = candidate
                    break
                }
                candidate.destroy()
            }
            if (target == null) target = Target(width, height, format).also { target = it; it.initialize() }
            val surface = target!!
            glDisable(GL_SCISSOR_TEST)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, state.draw)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, surface.backdrop)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, surface.framebuffer)
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT or (if (format != 0) GL_DEPTH_BUFFER_BIT else 0), GL_NEAREST)
        } catch (failure: Throwable) {
            target?.destroy()
            throw failure
        } finally {
            state.restore()
        }
        val surface = target!!
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, surface.framebuffer)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, surface.framebuffer)
        return Layer {
            // Restore the parent even if the content/composition pass throws.
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, state.draw)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, state.read)
            val compositeState = State()
            try {
                glDisable(GL_BLEND)
                glDisable(GL_DEPTH_TEST)
                // Honour an external stencil clip on the parent framebuffer during composition.
                glDisable(GL_CULL_FACE)
                glColorMask(true, true, true, true)
                glUseProgram(program)
                glBindVertexArray(vao)
                glBindBuffer(GL_ARRAY_BUFFER, vbo)
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, surface.backdrop)
                glActiveTexture(GL_TEXTURE1)
                glBindTexture(GL_TEXTURE_2D, surface.color)
                glUniform1i(glGetUniformLocation(program, "Backdrop"), 0)
                glUniform1i(glGetUniformLocation(program, "Content"), 1)
                glUniform2f(glGetUniformLocation(program, "Extent"), width.toFloat(), height.toFloat())
                if (difference) {
                    val full = TriangleSoup().apply {
                        tri(-1f, -1f, 1f, -1f, 1f, 1f, -1)
                        tri(-1f, -1f, 1f, 1f, -1f, 1f, -1)
                    }
                    drawMask(full, Matrix4f(), false)
                }
                drawMask(mask, projection, difference)
            } finally {
                compositeState.restore()
                pool.addLast(surface)
            }
        }
    }

    private fun depthFormat(framebuffer: Int): Int {
        if (framebuffer == 0) return when (glGetInteger(GL_DEPTH_BITS)) {
            0 -> 0
            16 -> GL_DEPTH_COMPONENT16
            24 -> GL_DEPTH_COMPONENT24
            else -> GL_DEPTH_COMPONENT32
        }
        if (glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL_NONE) return 0
        val bits = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE)
        val type = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE)
        // Query the attached object to preserve packed depth/stencil formats when present.
        val objectType = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
        val name = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)
        if (objectType == GL_RENDERBUFFER) {
            glBindRenderbuffer(GL_RENDERBUFFER, name)
            return glGetRenderbufferParameteri(GL_RENDERBUFFER, GL_RENDERBUFFER_INTERNAL_FORMAT)
        }
        if (objectType == GL_TEXTURE) {
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, name)
            val level = glGetFramebufferAttachmentParameteri(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)
            val internal = glGetTexLevelParameteri(GL_TEXTURE_2D, level, GL_TEXTURE_INTERNAL_FORMAT)
            if (internal != GL_DEPTH_COMPONENT) return internal
        }
        return when {
            type == GL_FLOAT -> GL_DEPTH_COMPONENT32F
            bits == 16 -> GL_DEPTH_COMPONENT16
            bits == 24 -> GL_DEPTH_COMPONENT24
            else -> GL_DEPTH_COMPONENT32
        }
    }

    private fun drawMask(mask: TriangleSoup, projection: Matrix4f, inverse: Boolean) {
        if (mask.colors.isEmpty()) return
        val data = FloatArray(mask.colors.size * 3)
        for (i in mask.colors.indices) {
            data[i * 3] = mask.positions[i * 2]
            data[i * 3 + 1] = mask.positions[i * 2 + 1]
            data[i * 3 + 2] = (mask.colors[i] ushr 24) / 255f
        }
        glUniformMatrix4fv(glGetUniformLocation(program, "Projection"), false, projection.get(FloatArray(16)))
        glUniform1i(glGetUniformLocation(program, "Inverse"), if (inverse) 1 else 0)
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW)
        glDrawArrays(GL_TRIANGLES, 0, mask.colors.size)
    }

    private fun ensureProgram() {
        if (program != 0) return
        fun shader(type: Int, source: String): Int {
            val id = glCreateShader(type)
            glShaderSource(id, source)
            glCompileShader(id)
            if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
                val log = glGetShaderInfoLog(id)
                glDeleteShader(id)
                error("Compose clip shader: $log")
            }
            return id
        }
        val vs = shader(GL_VERTEX_SHADER, """
            #version 330 core
            layout(location = 0) in vec2 Position;
            layout(location = 1) in float Coverage;
            uniform mat4 Projection;
            out float maskCoverage;
            void main() { gl_Position = Projection * vec4(Position, 0.0, 1.0); maskCoverage = Coverage; }
        """.trimIndent())
        var fs = 0
        val candidate = glCreateProgram()
        try {
            fs = shader(GL_FRAGMENT_SHADER, """
                #version 330 core
                uniform sampler2D Backdrop;
                uniform sampler2D Content;
                uniform vec2 Extent;
                uniform bool Inverse;
                in float maskCoverage;
                out vec4 fragColor;
                void main() {
                    vec2 uv = gl_FragCoord.xy / Extent;
                    float coverage = Inverse ? 1.0 - maskCoverage : maskCoverage;
                    fragColor = mix(texture(Backdrop, uv), texture(Content, uv), coverage);
                }
            """.trimIndent())
            glAttachShader(candidate, vs)
            glAttachShader(candidate, fs)
            glLinkProgram(candidate)
            check(glGetProgrami(candidate, GL_LINK_STATUS) != GL_FALSE) { glGetProgramInfoLog(candidate) }
            program = candidate
        } catch (failure: Throwable) {
            glDeleteProgram(candidate)
            throw failure
        } finally {
            glDeleteShader(vs)
            if (fs != 0) glDeleteShader(fs)
        }
        vao = glGenVertexArrays()
        vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 12, 0L)
        glVertexAttribPointer(1, 1, GL_FLOAT, false, 12, 8L)
    }

    fun destroyUnused() {
        while (pool.isNotEmpty()) pool.removeLast().destroy()
        if (program != 0) glDeleteProgram(program)
        if (vao != 0) glDeleteVertexArrays(vao)
        if (vbo != 0) glDeleteBuffers(vbo)
        program = 0; vao = 0; vbo = 0
    }
}
