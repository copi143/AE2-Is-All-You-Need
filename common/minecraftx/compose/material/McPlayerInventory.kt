package minecraftx.compose.material

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack

@Composable
fun McPlayerInventory(
    stacks: List<ItemStack>,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    colors: McColorScheme = McTheme.colors,
    onSlotClicked: ((index: Int, button: Int, clickType: ClickType) -> Unit)? = null,
) {
    val inventory = if (stacks.size >= 36) stacks else stacks + List(36 - stacks.size) { ItemStack.EMPTY }
    val main = inventory.take(27)
    val hotbar = inventory.drop(27).take(9)
    Column(modifier) {
        McItemGrid(
            stacks = main,
            columns = 9,
            interactive = interactive,
            colors = colors,
            onSlotClicked = onSlotClicked,
        )
        Spacer(Modifier.height(4.dp))
        McItemGrid(
            stacks = hotbar,
            columns = 9,
            interactive = interactive,
            colors = colors,
            onSlotClicked = onSlotClicked?.let { handler ->
                { index, button, clickType -> handler(index + 27, button, clickType) }
            },
        )
    }
}
