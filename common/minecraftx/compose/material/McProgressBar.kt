package minecraftx.compose.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme

@Composable
fun McProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    colors: McColorScheme = McTheme.colors,
    thickness: Dp = McTheme.shapes.progressThickness,
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(thickness) else Modifier.height(thickness))
            .background(colors.progressTrack)
            .drawBehind { drawRect(color = colors.buttonBorder, style = Stroke(1f)) },
    ) {
        if (vertical) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .background(colors.progressFill),
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(colors.progressFill),
            )
        }
    }
}
