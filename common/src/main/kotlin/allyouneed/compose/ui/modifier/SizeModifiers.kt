package allyouneed.compose.ui.modifier

import allyouneed.compose.ui.layout.Constraints
import allyouneed.compose.ui.layout.Measurable
import allyouneed.compose.ui.layout.Placeable

interface LayoutModifier : Modifier.Element {
    fun measure(constraints: Constraints, measurable: Measurable): Placeable
}

interface DrawModifier : Modifier.Element {
    fun draw(scope: allyouneed.compose.ui.draw.McDrawScope, drawContent: () -> Unit)
}

fun Modifier.size(width: Int, height: Int): Modifier = this then SizeModifier(width, width, height, height)
fun Modifier.size(size: Int): Modifier = this.size(size, size)
fun Modifier.width(width: Int): Modifier = this then SizeModifier(width, width, 0, Int.MAX_VALUE)
fun Modifier.height(height: Int): Modifier = this then SizeModifier(0, Int.MAX_VALUE, height, height)
fun Modifier.fillMaxWidth(): Modifier = this then FillMaxCrossAxisModifier(fillWidth = true, fillHeight = false)
fun Modifier.fillMaxHeight(): Modifier = this then FillMaxCrossAxisModifier(fillWidth = false, fillHeight = true)
fun Modifier.fillMaxSize(): Modifier = this.fillMaxWidth().fillMaxHeight()

private class SizeModifier(
    private val minW: Int, private val maxW: Int,
    private val minH: Int, private val maxH: Int,
) : LayoutModifier, Modifier.Element {
    override fun measure(constraints: Constraints, measurable: Measurable): Placeable {
        val clamped = Constraints(
            minWidth = maxOf(constraints.minWidth, minW.coerceAtMost(maxW)),
            maxWidth = minOf(constraints.maxWidth, maxW.coerceAtLeast(minW)),
            minHeight = maxOf(constraints.minHeight, minH.coerceAtMost(maxH)),
            maxHeight = minOf(constraints.maxHeight, maxH.coerceAtLeast(minH)),
        )
        return measurable.measure(clamped)
    }
}

private class FillMaxCrossAxisModifier(
    private val fillWidth: Boolean,
    private val fillHeight: Boolean,
) : LayoutModifier, Modifier.Element {
    override fun measure(constraints: Constraints, measurable: Measurable): Placeable {
        val p = measurable.measure(constraints)
        return Placeable(
            width = if (fillWidth) constraints.maxWidth else p.width,
            height = if (fillHeight) constraints.maxHeight else p.height,
            placeAt = p.placeAt,
        )
    }
}
