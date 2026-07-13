package allyouneed.terminal.pseudopattern

import appeng.api.implementations.menuobjects.IPortableTerminal
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.me.common.MEStorageMenu
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType

/**
 * Wireless version of the pseudo pattern terminal.
 */
class WirelessPseudoPatternTerminalMenu(
    id: Int,
    playerInv: Inventory,
    host: IPortableTerminal
) : MEStorageMenu(TYPE, id, playerInv, host, true) {

    companion object {
        val TYPE: MenuType<WirelessPseudoPatternTerminalMenu> =
            MenuTypeBuilder
                .create(::WirelessPseudoPatternTerminalMenu, IPortableTerminal::class.java)
                .build("wireless_pseudo_pattern_terminal")
    }
}
