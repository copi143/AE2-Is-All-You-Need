package ae2x.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import appeng.menu.interfaces.IProgressProvider
import minecraftx.compose.material.McProgressBar
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme

@Composable
fun AeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    colors: McColorScheme = McTheme.colors,
    thickness: Dp = McTheme.shapes.progressThickness,
) {
    McProgressBar(
        progress = progress,
        modifier = modifier,
        vertical = vertical,
        colors = colors,
        thickness = thickness,
    )
}

@Composable
fun AeProgressBar(
    provider: IProgressProvider,
    modifier: Modifier = Modifier,
    vertical: Boolean = true,
    colors: McColorScheme = McTheme.colors,
    thickness: Dp = McTheme.shapes.progressThickness,
) {
    val max = provider.maxProgress.coerceAtLeast(1)
    AeProgressBar(
        progress = provider.currentProgress.toFloat() / max.toFloat(),
        modifier = modifier,
        vertical = vertical,
        colors = colors,
        thickness = thickness,
    )
}
