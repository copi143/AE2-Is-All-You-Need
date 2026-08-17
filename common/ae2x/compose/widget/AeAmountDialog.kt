package ae2x.compose.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.material.McButton
import minecraftx.compose.material.McPanel
import minecraftx.compose.material.McText
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.world.item.ItemStack

@Composable
fun AeAmountDialog(
    title: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    min: Long = 1,
    max: Long = Long.MAX_VALUE,
    steps: List<Long> = listOf(1, 10, 100, 1000),
    stack: ItemStack = ItemStack.EMPTY,
    confirmLabel: String = "OK",
    cancelLabel: String = "Cancel",
    width: Dp = 160.dp,
    height: Dp = 90.dp,
    colors: McColorScheme = McTheme.colors,
) {
    McPanel(width = width, height = height, modifier = modifier, colors = colors) {
        Column(Modifier.padding(8.dp)) {
            McText(title, color = colors.textPrimary.value.toInt())
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!stack.isEmpty) {
                    ItemSlot(stack = stack, consumeClicks = false, colors = colors)
                    Spacer(Modifier.width(6.dp))
                }
                AeNumberEntry(
                    value = value,
                    onValueChange = onValueChange,
                    min = min,
                    max = max,
                    colors = colors,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                steps.forEach { step ->
                    McButton("+$step", onClick = { onValueChange((value + step).coerceIn(min, max)) }, colors = colors)
                    Spacer(Modifier.width(2.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                McButton(confirmLabel, onClick = onConfirm, colors = colors)
                Spacer(Modifier.width(6.dp))
                McButton(cancelLabel, onClick = onCancel, colors = colors)
            }
        }
    }
}
