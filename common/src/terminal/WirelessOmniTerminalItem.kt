package allyouneed.terminal

import appeng.api.implementations.menuobjects.ItemMenuHost
import appeng.helpers.WirelessTerminalMenuHost
import appeng.items.tools.powered.WirelessTerminalItem
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import org.jetbrains.annotations.Nullable
import java.util.function.DoubleSupplier

class WirelessOmniTerminalItem(props: Properties) :
    WirelessTerminalItem(DoubleSupplier { 1_600_000.0 }, props) {

    override fun getMenuType(): MenuType<*> = WirelessOmniTerminalMenu.TYPE

    @Nullable
    override fun getMenuHost(
        player: Player,
        inventorySlot: Int,
        stack: ItemStack,
        @Nullable pos: BlockPos?,
    ): ItemMenuHost {
        return WirelessTerminalMenuHost(player, inventorySlot, stack) { p, _ ->
            openFromInventory(p, inventorySlot, true)
        }
    }
}
