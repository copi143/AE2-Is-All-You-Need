package ae2x.compose.slot

import ae2x.compose.aeMenuSlot
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import appeng.menu.slot.IOptionalSlot
import minecraftx.compose.material.ItemSlot
import minecraftx.compose.theme.McColorScheme
import minecraftx.compose.theme.McTheme
import net.minecraft.world.inventory.Slot

@Composable
fun AeMenuSlot(
    slot: Slot,
    modifier: Modifier = Modifier,
    amount: String? = null,
    craftable: Boolean = false,
    missing: Boolean = false,
    colors: McColorScheme = McTheme.colors,
) {
    val optional = slot as? IOptionalSlot
    ItemSlot(
        stack = { slot.item },
        modifier = modifier.aeMenuSlot(slot),
        consumeClicks = false,
        amount = { amount },
        craftable = { craftable },
        disabled = optional?.isSlotEnabled == false,
        missing = missing,
        showTooltip = true,
        colors = colors,
    )
}
