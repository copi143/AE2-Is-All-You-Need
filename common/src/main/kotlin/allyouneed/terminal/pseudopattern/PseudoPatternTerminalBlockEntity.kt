package allyouneed.terminal.pseudopattern

import appeng.api.storage.ITerminalHost
import appeng.api.storage.MEStorage
import appeng.api.upgrades.IUpgradeInventory
import appeng.api.upgrades.UpgradeInventories
import appeng.api.util.IConfigManager
import appeng.blockentity.grid.AENetworkBlockEntity
import appeng.core.definitions.AEBlocks
import appeng.menu.ISubMenu
import appeng.menu.MenuOpener
import appeng.util.ConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * Block entity for the (wired) Pseudo Pattern Terminal.
 */
class PseudoPatternTerminalBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState
) : AENetworkBlockEntity(type, pos, state), ITerminalHost {

    private val configManager = ConfigManager { }

    override fun getInventory(): MEStorage? {
        val grid = mainNode.grid
        return grid?.storageService?.inventory
    }

    override fun getConfigManager(): IConfigManager = configManager

    override fun getUpgrades(): IUpgradeInventory =
        UpgradeInventories.forMachine(
            AEBlocks.PATTERN_PROVIDER,
            0
        ) { }

    override fun returnToMainMenu(player: Player, subMenu: ISubMenu) {
        MenuOpener.open(PseudoPatternTerminalMenu.TYPE, player, subMenu.locator, true)
    }

    override fun getMainMenuIcon(): ItemStack =
        ItemStack(AEBlocks.PATTERN_PROVIDER)
}
