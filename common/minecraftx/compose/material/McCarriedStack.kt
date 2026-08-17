package minecraftx.compose.material

import allyouneed.client.compose.platform.LocalMousePosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.world.item.ItemStack

@Composable
fun McCarriedStack(
    stack: ItemStack,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    if (stack.isEmpty) return
    val mouse = LocalMousePosition.current
    val density = LocalDensity.current
    val pos = mouse.inDensity(density)
    Box(modifier.offset { IntOffset(pos.x - 8, pos.y - 8) }) {
        ItemSlot(
            stack = stack,
            interactive = false,
            consumeClicks = false,
            colors = colors,
        )
    }
}
