package allyouneed.terminal

import appeng.api.implementations.menuobjects.IPortableTerminal
import appeng.api.storage.ITerminalHost
import appeng.helpers.WirelessTerminalMenuHost
import appeng.menu.me.common.MEStorageMenu
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Wireless version of the pseudo pattern terminal.
 */
class WirelessPseudoPatternTerminalMenu(
    id: Int,
    playerInv: net.minecraft.world.entity.player.Inventory,
    host: IPortableTerminal
) : MEStorageMenu(TYPE, id, playerInv, host, true) {

    companion object {
        val TYPE: net.minecraft.world.inventory.MenuType<WirelessPseudoPatternTerminalMenu> =
            appeng.menu.implementations.MenuTypeBuilder
                .create(::WirelessPseudoPatternTerminalMenu, IPortableTerminal::class.java)
                .build("wireless_pseudo_pattern_terminal")
    }
}
