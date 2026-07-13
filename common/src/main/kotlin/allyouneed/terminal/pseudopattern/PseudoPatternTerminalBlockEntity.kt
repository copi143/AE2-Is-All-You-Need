package allyouneed.terminal

import appeng.api.storage.ITerminalHost
import appeng.api.storage.MEStorage
import appeng.api.upgrades.IUpgradeInventory
import appeng.api.upgrades.UpgradeInventories
import appeng.api.util.IConfigManager
import appeng.blockentity.grid.AENetworkBlockEntity
import appeng.menu.ISubMenu
import appeng.menu.MenuOpener
import appeng.menu.locator.MenuLocators
import appeng.util.ConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
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
            appeng.core.definitions.AEBlocks.PATTERN_PROVIDER,
            0
        ) { }

    override fun returnToMainMenu(player: Player, subMenu: ISubMenu) {
        MenuOpener.open(PseudoPatternTerminalMenu.TYPE, player, subMenu.getLocator(), true)
    }

    override fun getMainMenuIcon(): net.minecraft.world.item.ItemStack =
        net.minecraft.world.item.ItemStack(appeng.core.definitions.AEBlocks.PATTERN_PROVIDER)
}
