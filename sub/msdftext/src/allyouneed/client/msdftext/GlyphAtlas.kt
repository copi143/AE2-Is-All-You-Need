package allyouneed.client.msdftext

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

data class GlyphKey(val family: String, val codePoint: Int)

class AtlasSlot(
    val key: GlyphKey,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val originX: Float,
    val originY: Float,
    val pxRange: Float,
)

class GlyphAtlas(
    initialSize: Int = 512,
    private val maxSize: Int = 2048,
    private val gap: Int = 1,
) {
    var size: Int = initialSize
        private set
    var pixels: ByteArray = ByteArray(size * size * 4)
        private set
    private val slots = HashMap<GlyphKey, AtlasSlot>()
    private var cursorX = gap
    private var cursorY = gap
    private var shelfH = 0
    var textureId: Int = 0
        private set
    private var gpuSize: Int = 0

    operator fun get(key: GlyphKey): AtlasSlot? = slots[key]

    fun pack(key: GlyphKey, bitmap: MsdfBitmap): AtlasSlot? {
        slots[key]?.let { return it }
        val w = bitmap.width
        val h = bitmap.height
        if (w + gap * 2 > size || h + gap * 2 > size) return null
        if (cursorX + w + gap > size) {
            cursorX = gap
            cursorY += shelfH + gap
            shelfH = 0
        }
        if (cursorY + h + gap > size) {
            if (!grow()) return null
            return pack(key, bitmap)
        }
        val slot = AtlasSlot(key, cursorX, cursorY, w, h, bitmap.originX, bitmap.originY, bitmap.pxRange)
        blit(slot, bitmap.pixels)
        slots[key] = slot
        cursorX += w + gap
        if (h > shelfH) shelfH = h
        return slot
    }

    fun ensureTexture() {
        if (textureId == 0) {
            textureId = GL11.glGenTextures()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            uploadFull()
            return
        }
        if (gpuSize != size) uploadFull()
    }

    fun upload(slot: AtlasSlot) {
        if (textureId == 0) return
        val buf = copyRect(slot)
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            resetUnpack()
            GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D, 0,
                slot.x, slot.y, slot.width, slot.height,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf,
            )
        } finally {
            MemoryUtil.memFree(buf)
        }
    }

    fun destroy() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId)
            textureId = 0
            gpuSize = 0
        }
    }

    private fun grow(): Boolean {
        val next = size * 2
        if (next > maxSize) return false
        val grown = ByteArray(next * next * 4)
        val old = size
        val src = pixels
        for (y in 0 until old) {
            System.arraycopy(src, y * old * 4, grown, y * next * 4, old * 4)
        }
        pixels = grown
        size = next
        return true
    }

    private fun blit(slot: AtlasSlot, rgba: ByteArray) {
        val dst = pixels
        val stride = size * 4
        val w = slot.width
        for (y in 0 until slot.height) {
            System.arraycopy(rgba, y * w * 4, dst, (slot.y + y) * stride + slot.x * 4, w * 4)
        }
    }

    private fun uploadFull() {
        val buf = MemoryUtil.memAlloc(pixels.size)
        try {
            buf.put(pixels).flip()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId)
            resetUnpack()
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
                size, size, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf,
            )
            gpuSize = size
        } finally {
            MemoryUtil.memFree(buf)
        }
    }

    private fun resetUnpack() {
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
    }

    private fun copyRect(slot: AtlasSlot): ByteBuffer {
        val w = slot.width
        val h = slot.height
        val buf = MemoryUtil.memAlloc(w * h * 4)
        val stride = size * 4
        val src = pixels
        for (y in 0 until h) {
            val row = (slot.y + y) * stride + slot.x * 4
            buf.put(src, row, w * 4)
        }
        buf.flip()
        return buf
    }
}
