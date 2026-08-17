package ae2x.compose

import kotlin.math.roundToInt

object AeSlotGeometry {
    const val ITEM_INSET = 1
    const val ITEM_SIZE = 16
    const val HIDDEN = -9999

    fun toSlotPos(windowX: Float, windowY: Float, uiScale: Float, guiLeft: Int, guiTop: Int): IntPair {
        val x = (windowX * uiScale).roundToInt() + ITEM_INSET - guiLeft
        val y = (windowY * uiScale).roundToInt() + ITEM_INSET - guiTop
        return IntPair(x, y)
    }

    fun ghostX(guiLeft: Int, slotX: Int): Int = guiLeft + slotX

    fun ghostY(guiTop: Int, slotY: Int): Int = guiTop + slotY
}

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

data class IntRect(val x: Int, val y: Int, val width: Int, val height: Int)
