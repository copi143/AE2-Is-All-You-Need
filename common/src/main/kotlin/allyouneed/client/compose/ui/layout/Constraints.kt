package allyouneed.client.compose.ui.layout

data class Constraints(
    val minWidth: Int = 0,
    val maxWidth: Int = Int.MAX_VALUE,
    val minHeight: Int = 0,
    val maxHeight: Int = Int.MAX_VALUE,
) {
    companion object

    fun fixed(width: Int, height: Int) = Constraints(width, width, height, height)

    fun fillMax() = Constraints(0, Int.MAX_VALUE, 0, Int.MAX_VALUE)

    fun constrainWidth(width: Int) = width.coerceIn(minWidth, maxWidth)
    fun constrainHeight(height: Int) = height.coerceIn(minHeight, maxHeight)
}

interface Measurable {
    fun measure(constraints: Constraints): Placeable
}
