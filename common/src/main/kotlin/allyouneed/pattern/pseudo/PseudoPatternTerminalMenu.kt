package allyouneed.pattern.pseudo

import appeng.api.storage.ITerminalHost
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.me.common.MEStorageMenu
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType

/**
 * Menu for the Pseudo Pattern Terminal (Block version).
 * This is a read-only list of pseudo patterns found on the network.
 */
class PseudoPatternTerminalMenu(
    id: Int,
    playerInventory: Inventory,
    host: ITerminalHost
) : appeng.menu.me.common.MEStorageMenu(TYPE, id, playerInventory, host, true) {

    companion object {
        val TYPE: MenuType<PseudoPatternTerminalMenu> = MenuTypeBuilder
            .create(::PseudoPatternTerminalMenu, ITerminalHost::class.java)
            .build("pseudo_pattern_terminal")
    }

    // For a future richer implementation we can expose a list of pseudo patterns here.
    // The screen will query the network via the host.
}
