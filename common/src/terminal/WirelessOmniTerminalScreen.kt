package allyouneed.terminal

import ae2x.compose.screen.AeComposeMEScreen
import ae2x.compose.screen.AeTerminalScaffold
import androidx.compose.runtime.Composable
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class WirelessOmniTerminalScreen(
    menu: WirelessOmniTerminalMenu,
    playerInventory: Inventory,
    title: Component,
) : AeComposeMEScreen<WirelessOmniTerminalMenu>(menu, playerInventory, title) {

    @Composable
    override fun Content() {
        AeTerminalScaffold(screen = this, title = title.string)
    }
}
