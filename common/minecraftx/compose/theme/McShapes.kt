package minecraftx.compose.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry knobs for the framework's chrome; the Minecraft look is flat 1px borders with square corners. */
class McShapes(
    val slotSize: Dp = 18.dp,
    val buttonHeight: Dp = 16.dp,
    val iconButtonSize: Dp = 16.dp,
    val tabHeight: Dp = 16.dp,
    val progressThickness: Dp = 6.dp,
) {
    companion object {
        val Default = McShapes()
    }
}
