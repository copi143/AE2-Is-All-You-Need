package minecraftx.compose.material

import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.McGraphics
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.world.item.ItemStack

@Composable
fun McCarriedStack(
    stack: () -> ItemStack,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    val mouse = LocalMousePosition.current
    val density = LocalDensity.current
    val renderer = remember { SlotRenderers.get() }
    Box(
        modifier.drawBehind {
            val held = stack()
            if (held.isEmpty) return@drawBehind
            val graphics = McGraphics.current ?: return@drawBehind
            val p = mouse.inDensity(density)
            graphics.pose().pushPose()
            graphics.pose().translate((p.x - 8).toFloat(), (p.y - 8).toFloat(), 400f)
            renderer.drawStack(graphics, held, 0, 0)
            graphics.pose().popPose()
        },
    )
}

@Composable
fun McCarriedStack(
    stack: ItemStack,
    modifier: Modifier = Modifier,
    colors: McColorScheme = McTheme.colors,
) {
    McCarriedStack(stack = { stack }, modifier = modifier, colors = colors)
}
