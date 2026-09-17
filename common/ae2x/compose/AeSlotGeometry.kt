package ae2x.compose

import kotlin.math.roundToInt

object AeSlotGeometry {
    const val ITEM_INSET = 1
    const val ITEM_SIZE = 16
    const val HIDDEN = -9999

    fun scaledInset(uiScale: Float): Int = (ITEM_INSET * uiScale).roundToInt()

    fun scaledItemSize(uiScale: Float): Int = (ITEM_SIZE * uiScale).roundToInt()

    fun toSlotPos(windowX: Float, windowY: Float, uiScale: Float, guiLeft: Int, guiTop: Int): IntPair {
        val inset = scaledInset(uiScale)
        val x = (windowX * uiScale).roundToInt() + inset - guiLeft
        val y = (windowY * uiScale).roundToInt() + inset - guiTop
        return IntPair(x, y)
    }

    fun ghostX(guiLeft: Int, slotX: Int): Int = guiLeft + slotX

    fun ghostY(guiTop: Int, slotY: Int): Int = guiTop + slotY

    fun union(rects: List<IntRect>): IntRect? {
        if (rects.isEmpty()) return null
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (rect in rects) {
            minX = minOf(minX, rect.x)
            minY = minOf(minY, rect.y)
            maxX = maxOf(maxX, rect.x + rect.width)
            maxY = maxOf(maxY, rect.y + rect.height)
        }
        return IntRect(minX, minY, maxX - minX, maxY - minY)
    }
}

@JvmRecord
data class IntPair(val x: Int, val y: Int)

class ExclusionAccumulator {
    private val zones = ArrayList<IntRect>(4)

    fun beginFrame() {
        zones.clear()
    }

    fun add(x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        zones += IntRect(x, y, width, height)
    }

    fun snapshot(): List<IntRect> = zones.toList()
}

@JvmRecord
data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int)
