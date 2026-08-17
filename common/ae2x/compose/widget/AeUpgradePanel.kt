package ae2x.compose.widget

import ae2x.compose.LocalAeHost
import ae2x.compose.slot.AeSlotGrid
import ae2x.compose.slotsOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import appeng.menu.SlotSemantics

@Composable
fun AeUpgradePanel(
    modifier: Modifier = Modifier,
    columns: Int = 1,
) {
    val menu = LocalAeHost.current.menu
    AeSlotGrid(SlotSemantics.UPGRADE.slotsOf(menu), columns = columns, modifier = modifier)
}
