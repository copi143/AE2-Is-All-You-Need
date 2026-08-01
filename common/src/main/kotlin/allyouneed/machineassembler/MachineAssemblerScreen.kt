package allyouneed.machineassembler

import appeng.client.gui.implementations.UpgradeableScreen
import appeng.client.gui.style.ScreenStyle
import appeng.client.gui.widgets.ProgressBar
import appeng.client.gui.widgets.ProgressBar.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class MachineAssemblerScreen(
    menu: MachineAssemblerMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle,
) : UpgradeableScreen<MachineAssemblerMenu>(menu, playerInventory, title, style) {

    private val pb: ProgressBar

    init {
        this.pb = ProgressBar(this.menu, style.getImage("progressBar"), Direction.VERTICAL)
        widgets.add("progressBar", this.pb)
    }

    override fun updateBeforeRender() {
        super.updateBeforeRender()
        this.pb.setFullMsg(Component.literal("${this.menu.getCurrentProgress()}%"))
    }
}
