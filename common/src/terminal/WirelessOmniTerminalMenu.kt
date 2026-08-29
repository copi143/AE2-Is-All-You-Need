package allyouneed.terminal

import appeng.api.implementations.menuobjects.IPortableTerminal
import appeng.menu.implementations.MenuTypeBuilder
import appeng.menu.me.common.MEStorageMenu
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.MenuType

class WirelessOmniTerminalMenu(
    id: Int,
    playerInv: Inventory,
    host: IPortableTerminal,
) : MEStorageMenu(TYPE, id, playerInv, host, true) {

    companion object {
        val TYPE: MenuType<WirelessOmniTerminalMenu> =
            MenuTypeBuilder
                .create(::WirelessOmniTerminalMenu, IPortableTerminal::class.java)
                .build("wireless_omni_terminal")
    }
}
