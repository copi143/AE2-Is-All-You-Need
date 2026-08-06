package allyouneed.pattern.pseudo

import appeng.client.gui.me.common.MEStorageScreen
import appeng.client.gui.style.ScreenStyle
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * Simple screen for the Pseudo Pattern Terminal.
 * For a minimal first implementation we reuse the ME storage screen background.
 * A richer implementation will list pseudo patterns and provide "push" buttons.
 */
class PseudoPatternTerminalScreen(
    menu: PseudoPatternTerminalMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle
) : appeng.client.gui.me.common.MEStorageScreen<PseudoPatternTerminalMenu>(menu, playerInventory, title, style) {

    // TODO: Render list of pseudo patterns discovered on the network and "push" actions.
    // The network inventory is accessible via menu.getHost().getInventory()
}
