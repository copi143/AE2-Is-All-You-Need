package minecraftx.compose.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Geometry knobs for the framework's chrome. The Minecraft look is flat 1px borders with square
 * corners, so [borderWidth] is the only actively used slot today; the rest is reserved so themes
 * can grow without changing the component signatures.
 */
class McShapes(
    val borderWidth: Dp = 1.dp,
    val slotSize: Dp = 18.dp,
    val closeButtonSize: Dp = 14.dp,
) {
    companion object {
        val Default = McShapes()
    }
}
