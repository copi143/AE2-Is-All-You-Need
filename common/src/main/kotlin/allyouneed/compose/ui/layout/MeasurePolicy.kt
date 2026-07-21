package allyouneed.compose.ui.layout

fun interface MeasurePolicy {
    fun measure(
        scope: MeasureScope,
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult
}

class MeasureScope(
    val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) {
    var density: Float = 1f
}

enum class LayoutDirection { Ltr, Rtl }
