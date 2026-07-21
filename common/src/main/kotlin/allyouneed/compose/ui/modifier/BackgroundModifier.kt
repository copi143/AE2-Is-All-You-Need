package allyouneed.compose.ui.modifier

import allyouneed.compose.ui.draw.McDrawScope

fun Modifier.background(color: Int): Modifier = this then BackgroundModifier(color)

private class BackgroundModifier(
    private val color: Int,
) : DrawModifier, Modifier.Element {
    override fun draw(scope: McDrawScope, drawContent: () -> Unit) {
        scope.drawBehind(color)
        drawContent()
    }
}
