package allyouneed.compose.ui.modifier

interface Modifier {
    infix fun then(other: Modifier): Modifier = if (other === Modifier) this else CombinedModifier(this, other)

    interface Element : Modifier

    companion object : Modifier
}

class CombinedModifier(
    val outer: Modifier,
    val inner: Modifier,
) : Modifier

fun Modifier.foldElements(action: (Modifier.Element) -> Unit) {
    when (this) {
        is Modifier.Element -> action(this)
        is CombinedModifier -> {
            outer.foldElements(action)
            inner.foldElements(action)
        }
    }
}
