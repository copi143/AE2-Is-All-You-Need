package allyouneed.iodrive

import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import appeng.client.gui.AEBaseScreen
import appeng.client.gui.style.ScreenStyle

class MEIODriveScreen(
    menu: MEIODriveMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle,
) : AEBaseScreen<MEIODriveMenu>(menu, playerInventory, title, style) {

    init {
        widgets.addOpenPriorityButton()
    }
}
