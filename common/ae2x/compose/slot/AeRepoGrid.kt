package ae2x.compose.slot

import ae2x.compose.LocalAeHost
import ae2x.compose.AeSlotGeometry
import ae2x.compose.format.AeAmountFormat
import allyouneed.client.compose.platform.LocalMousePosition
import allyouneed.client.compose.platform.LocalUiScale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import appeng.api.stacks.GenericStack
import appeng.client.gui.StackWithBounds
import appeng.client.gui.me.common.Repo
import appeng.menu.me.common.GridInventoryEntry
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.theme.McTheme
import net.minecraft.client.renderer.Rect2i
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

@Composable
fun AeRepoGrid(
    repo: Repo,
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    onEntryClick: (GridInventoryEntry?, button: Int, clickType: ClickType) -> Unit,
) {
    val slotSize = McTheme.shapes.slotSize
    val slotPx = slotSize.value
    val visible = rows * columns
    val host = LocalAeHost.current
    val mouse = LocalMousePosition.current
    val uiScale = LocalUiScale.current
    var gridPos by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .size(slotSize * columns, slotSize * rows)
            .onGloballyPositioned { gridPos = it.positionInWindow() },
    ) {
        val pointer = mouse.position
        repeat(visible) { index ->
            val entry = repo.get(index)
            val col = index % columns
            val row = index / columns
            val cellX = gridPos.x + col * slotPx
            val cellY = gridPos.y + row * slotPx
            if (pointer.x.toFloat() in cellX..(cellX + slotPx) &&
                pointer.y.toFloat() in cellY..(cellY + slotPx)
            ) {
                val what = entry?.what
                if (what != null) {
                    host.reportHoverStack(
                        StackWithBounds(
                            GenericStack(what, entry.storedAmount),
                            Rect2i(
                                (cellX * uiScale).roundToInt() + AeSlotGeometry.ITEM_INSET,
                                (cellY * uiScale).roundToInt() + AeSlotGeometry.ITEM_INSET,
                                AeSlotGeometry.ITEM_SIZE,
                                AeSlotGeometry.ITEM_SIZE,
                            ),
                        ),
                    )
                }
            }
            ItemSlot(
                stack = {
                    repo.get(index)?.what?.wrapForDisplayOrFilter() ?: ItemStack.EMPTY
                },
                modifier = Modifier.offset(slotSize * col, slotSize * row),
                interactive = true,
                consumeClicks = true,
                amount = {
                    repo.get(index)?.storedAmount?.takeIf { it > 0 }?.let { AeAmountFormat.slot(it) }
                },
                craftable = { repo.get(index)?.isCraftable == true },
                onSlotClicked = { button, clickType -> onEntryClick(repo.get(index), button, clickType) },
            )
        }
    }
}

private operator fun androidx.compose.ui.unit.Dp.times(count: Int) = this * count.toFloat()
