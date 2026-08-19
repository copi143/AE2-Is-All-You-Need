package allyouneed.parts.iodrive

import appeng.client.gui.AEBaseScreen
import appeng.client.gui.style.ScreenStyle
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class MEIODriveScreen(
    menu: MEIODriveMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle,
) : appeng.client.gui.AEBaseScreen<MEIODriveMenu>(menu, playerInventory, title, style) {

    init {
        widgets.addOpenPriorityButton()
    }
}
