package allyouneed.compose.platform

import allyouneed.client.compose.platform.ClipGeometry
import allyouneed.client.compose.platform.McClipTarget
import allyouneed.client.compose.platform.TriangleSoup
import allyouneed.client.compose.platform.fillContours
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.flattenContours
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryStack

class ClipTargetGlTest {
    private val projection = Matrix4f().ortho(0f, 64f, 0f, 64f, -1f, 1f)

    private fun mask(path: Path, matrix: Matrix4f = Matrix4f()): TriangleSoup =
        fillContours(path.flattenContours(0.1f).map { ClipGeometry.transform(it, matrix) }, path.fillType, -1)

    private fun rectangle(l: Float, b: Float, r: Float, t: Float) = Path().apply { addRect(Rect(l, b, r, t)) }

    private fun clear(r: Float, g: Float, b: Float, a: Float = 1f) {
        glClearColor(r, g, b, a)
        glClear(GL_COLOR_BUFFER_BIT)
    }

    private fun pixel(x: Int, y: Int): IntArray = MemoryStack.stackPush().use { stack ->
        val bytes = stack.malloc(4)
        glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bytes)
        IntArray(4) { bytes[it].toInt() and 255 }
    }

    @Test
    fun `offscreen masks preserve pixels alpha nesting difference and GL bindings`() {
        assumeTrue(glfwInit(), "GL test requires a display (run with xvfb-run on headless machines)")
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        val window = glfwCreateWindow(64, 64, "clip-test", 0, 0)
        try {
            assertNotEquals(0L, window)
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            val parent = glGenFramebuffers()
            val color = glGenTextures()
            val depth = glGenRenderbuffers()
            try {
                glBindFramebuffer(GL_FRAMEBUFFER, parent)
                glBindTexture(GL_TEXTURE_2D, color)
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 64, 64, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0)
                glBindRenderbuffer(GL_RENDERBUFFER, depth)
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, 64, 64)
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth)
                assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER))
                glViewport(0, 0, 64, 64)
                glClearDepth(1.0)
                glClear(GL_DEPTH_BUFFER_BIT)

                clear(1f, 0f, 0f, 0.4f)
                val rounded = Path().apply { addRoundRect(RoundRect(Rect(8f, 8f, 56f, 56f), CornerRadius(16f))) }
                val outer = McClipTarget.begin(mask(rounded), projection, false)
                clear(0f, 0f, 1f, 0.7f)
                val inner = McClipTarget.begin(mask(rectangle(0f, 0f, 32f, 64f)), projection, false)
                clear(0f, 1f, 0f)
                inner.finish()
                outer.finish()
                assertArrayEquals(intArrayOf(255, 0, 0, 102), pixel(8, 8))
                assertArrayEquals(intArrayOf(0, 255, 0, 255), pixel(20, 32))
                assertArrayEquals(intArrayOf(0, 0, 255, 178), pixel(44, 32))
                assertEquals(parent, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING))
                assertEquals(parent, glGetInteger(GL_READ_FRAMEBUFFER_BINDING))

                clear(1f, 0f, 0f)
                val rotation = Matrix4f().translate(32f, 32f, 0f).rotateZ((Math.PI / 4).toFloat())
                val rotated = McClipTarget.begin(mask(rectangle(-12f, -12f, 12f, 12f), rotation), projection, false)
                clear(0f, 1f, 0f)
                rotated.finish()
                assertArrayEquals(intArrayOf(0, 255, 0, 255), pixel(32, 32))
                assertArrayEquals(intArrayOf(255, 0, 0, 255), pixel(17, 17))

                clear(1f, 0f, 0f)
                val difference = McClipTarget.begin(mask(rounded), projection, true)
                clear(0f, 1f, 0f)
                difference.finish()
                assertArrayEquals(intArrayOf(255, 0, 0, 255), pixel(32, 32))
                assertArrayEquals(intArrayOf(0, 255, 0, 255), pixel(2, 2))

                // Existing scissor remains active across target setup/composition.
                clear(1f, 0f, 0f)
                glEnable(GL_SCISSOR_TEST)
                glScissor(0, 0, 32, 64)
                val clipped = McClipTarget.begin(mask(rounded), projection, false)
                clear(0f, 1f, 0f)
                clipped.finish()
                assertTrue(glIsEnabled(GL_SCISSOR_TEST))
                assertArrayEquals(intArrayOf(255, 0, 0, 255), pixel(44, 32))
                assertArrayEquals(intArrayOf(0, 255, 0, 255), pixel(20, 32))
                glDisable(GL_SCISSOR_TEST)
                assertEquals(GL_NO_ERROR, glGetError())
            } finally {
                McClipTarget.destroyUnused()
                glBindFramebuffer(GL_FRAMEBUFFER, 0)
                glDeleteFramebuffers(parent)
                glDeleteTextures(color)
                glDeleteRenderbuffers(depth)
            }
        } finally {
            if (window != 0L) glfwDestroyWindow(window)
            glfwTerminate()
        }
    }
}
