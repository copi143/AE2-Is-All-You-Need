package minecraftx.compose.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
    val style = McTheme.style
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(thickness) else Modifier.height(thickness))
            .drawBehind { with(style) { progressTrackChrome(colors) } },
    ) {
        if (vertical) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .drawBehind { with(style) { progressFillChrome(colors) } },
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .drawBehind { with(style) { progressFillChrome(colors) } },
            )
        }
    }
}
