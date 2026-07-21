package allyouneed.compose.ui.modifier

import allyouneed.compose.ui.layout.Constraints
import allyouneed.compose.ui.layout.Measurable
import allyouneed.compose.ui.layout.Placeable

fun Modifier.padding(all: Int): Modifier = this then PaddingModifier(all, all, all, all)
fun Modifier.padding(horizontal: Int = 0, vertical: Int = 0): Modifier =
    this then PaddingModifier(horizontal, horizontal, vertical, vertical)

private class PaddingModifier(
    private val left: Int, private val right: Int,
    private val top: Int, private val bottom: Int,
) : LayoutModifier, Modifier.Element {
    override fun measure(constraints: Constraints, measurable: Measurable): Placeable {
        val inner = Constraints(
            minWidth = (constraints.minWidth - left - right).coerceAtLeast(0),
            maxWidth = (constraints.maxWidth - left - right).coerceAtLeast(0),
            minHeight = (constraints.minHeight - top - bottom).coerceAtLeast(0),
            maxHeight = (constraints.maxHeight - top - bottom).coerceAtLeast(0),
        )
        val p = measurable.measure(inner)
        return Placeable(
            width = p.width + left + right,
            height = p.height + top + bottom,
        ) { x, y ->
            p.place(x + left, y + top)
        }
    }
}

fun Modifier.offset(x: Int = 0, y: Int = 0): Modifier = this then OffsetModifier(x, y)

private class OffsetModifier(
    private val dx: Int,
    private val dy: Int,
) : LayoutModifier, Modifier.Element {
    override fun measure(constraints: Constraints, measurable: Measurable): Placeable {
        val p = measurable.measure(constraints)
        return Placeable(p.width + dx, p.height + dy) { x, y ->
            p.place(x + dx, y + dy)
        }
    }
}
