package ae2x.compose.slot

import ae2x.compose.format.AeAmountFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import appeng.client.gui.me.common.Repo
import appeng.menu.me.common.GridInventoryEntry
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.theme.McTheme
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack

@Composable
fun AeRepoGrid(
    repo: Repo,
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    onEntryClick: (GridInventoryEntry?, button: Int, clickType: ClickType) -> Unit,
) {
    val slotSize = McTheme.shapes.slotSize
    val visible = rows * columns
    Box(modifier.size(slotSize * columns, slotSize * rows)) {
        repeat(visible) { index ->
            val entry = repo.get(index)
            val stack = entry?.what?.wrapForDisplayOrFilter() ?: ItemStack.EMPTY
            val col = index % columns
            val row = index / columns
            ItemSlot(
                stack = stack,
                modifier = Modifier.offset(slotSize * col, slotSize * row),
                interactive = true,
                consumeClicks = true,
                amount = entry?.storedAmount?.takeIf { it > 0 }?.let { AeAmountFormat.slot(it) },
                craftable = entry?.isCraftable == true,
                onSlotClicked = { button, clickType -> onEntryClick(entry, button, clickType) },
            )
        }
    }
}

private operator fun androidx.compose.ui.unit.Dp.times(count: Int) = this * count.toFloat()
