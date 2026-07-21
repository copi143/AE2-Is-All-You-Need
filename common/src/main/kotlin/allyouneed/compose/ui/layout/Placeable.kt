package allyouneed.compose.ui.layout

class Placeable(
    val width: Int,
    val height: Int,
    internal val placeAt: (Int, Int) -> Unit,
) {
    fun place(x: Int, y: Int) = placeAt(x, y)
}

data class MeasureResult(
    val width: Int,
    val height: Int,
    val placeables: List<Placeable>,
    val placeChildren: () -> Unit,
)
