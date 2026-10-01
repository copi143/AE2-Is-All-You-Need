package allyouneed.compose.platform

import allyouneed.client.compose.platform.fillContours
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.flattenContours
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class TriangleDebugTest2 {
    @Test
    fun `single triangle at 10 44`() {
        val p = Path().apply {
            moveTo(10f, 44f)
            lineTo(46f, 44f)
            lineTo(28f, 12f)
            close()
        }
        val cs = p.flattenContours()
        println("cs=${cs.size} pts=${cs.firstOrNull()?.size} bounds=${p.getBounds()}")
        cs.firstOrNull()?.let { println(it.joinToString()) }
        val soup = fillContours(cs, PathFillType.NonZero, -1)
        println("tris=${soup.positions.size/6}")
        assertEquals(1, soup.positions.size/6)
    }
}
