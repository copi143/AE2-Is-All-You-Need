package ae2x.compose.slot

import ae2x.compose.LocalAeHost
import ae2x.compose.slotsOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import appeng.menu.SlotSemantic
import minecraftx.compose.theme.McTheme
import net.minecraft.world.inventory.Slot

@Composable
fun AeSlotGrid(
    slots: List<Slot>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val slotSize = McTheme.shapes.slotSize
    val rows = if (columns <= 0) 0 else (slots.size + columns - 1) / columns
    Box(modifier.size(slotSize * columns, slotSize * rows)) {
        slots.forEachIndexed { index, slot ->
            val col = index % columns
            val row = index / columns
            AeMenuSlot(
                slot = slot,
                modifier = Modifier.offset(slotSize * col, slotSize * row),
            )
        }
    }
}

@Composable
fun AeSlotGrid(
    semantic: SlotSemantic,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val host = LocalAeHost.current
    AeSlotGrid(semantic.slotsOf(host.menu), columns, modifier)
}

private operator fun androidx.compose.ui.unit.Dp.times(count: Int) = this * count.toFloat()
