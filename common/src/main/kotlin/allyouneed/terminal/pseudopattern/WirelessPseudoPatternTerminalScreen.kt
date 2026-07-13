package allyouneed.terminal

import appeng.client.gui.me.common.MEStorageScreen
import appeng.client.gui.style.ScreenStyle
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * Screen for the Wireless Pseudo Pattern Terminal.
 * Reuses ME storage screen for now; later can be specialized.
 */
class WirelessPseudoPatternTerminalScreen(
    menu: WirelessPseudoPatternTerminalMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle
) : MEStorageScreen<WirelessPseudoPatternTerminalMenu>(menu, playerInventory, title, style) {
}