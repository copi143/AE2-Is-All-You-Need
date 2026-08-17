package ae2x.compose.slot

import ae2x.compose.LocalAeHost
import ae2x.compose.slotsOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import appeng.menu.SlotSemantics

@Composable
fun AePlayerInventory(modifier: Modifier = Modifier) {
    val menu = LocalAeHost.current.menu
    Column(modifier) {
        AeSlotGrid(SlotSemantics.PLAYER_INVENTORY.slotsOf(menu), columns = 9)
        Spacer(Modifier.height(4.dp))
        AeSlotGrid(SlotSemantics.PLAYER_HOTBAR.slotsOf(menu), columns = 9)
    }
}
