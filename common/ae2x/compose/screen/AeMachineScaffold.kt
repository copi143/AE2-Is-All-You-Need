package ae2x.compose.screen

import androidx.compose.ui.graphics.toArgb
import ae2x.compose.aePanelBounds
import ae2x.compose.slot.AePlayerInventory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import minecraftx.compose.material.McPanel
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McTheme

@Composable
fun AeMachineScaffold(
    title: String,
    modifier: Modifier = Modifier,
    width: Dp = 176.dp,
    height: Dp = 166.dp,
    leftBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Top) {
            leftBar()
            McPanel(width = width, height = height, modifier = modifier.aePanelBounds()) {
                Column(Modifier.padding(7.dp)) {
                    McText(title, color = McTheme.colors.textPrimary.toArgb())
                    Spacer(Modifier.height(4.dp))
                    content()
                    Spacer(Modifier.height(6.dp))
                    AePlayerInventory()
                }
            }
        }
    }
}
