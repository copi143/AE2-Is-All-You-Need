package minecraftx.compose.material

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack

@Composable
fun McItemGrid(
    stacks: List<ItemStack>,
    columns: Int,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    colors: McColorScheme = McTheme.colors,
    onSlotClicked: ((index: Int, button: Int, clickType: ClickType) -> Unit)? = null,
) {
    val slot = McTheme.shapes.slotSize
    val rows = if (columns <= 0) 0 else (stacks.size + columns - 1) / columns
    Box(modifier.size(slot * columns, slot * rows)) {
        stacks.forEachIndexed { index, stack ->
            val col = index % columns
            val row = index / columns
            ItemSlot(
                stack = stack,
                modifier = Modifier.offset(slot * col, slot * row),
                interactive = interactive,
                onSlotClicked = onSlotClicked?.let { handler ->
                    { button, clickType -> handler(index, button, clickType) }
                },
                colors = colors,
            )
        }
    }
}

private operator fun androidx.compose.ui.unit.Dp.times(count: Int) = this * count.toFloat()
