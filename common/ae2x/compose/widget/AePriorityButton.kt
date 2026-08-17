package ae2x.compose.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import appeng.core.localization.GuiText
import appeng.core.sync.network.NetworkHandler
import appeng.core.sync.packets.SwitchGuisPacket
import appeng.menu.implementations.PriorityMenu
import minecraftx.compose.material.McButton

@Composable
fun AePriorityButton(modifier: Modifier = Modifier) {
    McButton(
        label = GuiText.Priority.text().string,
        onClick = { NetworkHandler.instance().sendToServer(SwitchGuisPacket.openSubMenu(PriorityMenu.TYPE)) },
        modifier = modifier,
    )
}
