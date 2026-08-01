package allyouneed.pattern.machine

import appeng.api.storage.MEStorage
import appeng.api.upgrades.IUpgradeInventory
import appeng.api.upgrades.UpgradeInventories
import appeng.api.util.IConfigManager
import appeng.blockentity.grid.AENetworkBlockEntity
import appeng.core.definitions.AEBlocks
import appeng.helpers.IPatternTerminalLogicHost
import appeng.helpers.IPatternTerminalMenuHost
import appeng.menu.ISubMenu
import appeng.menu.MenuOpener
import appeng.parts.encoding.PatternEncodingLogic
import appeng.util.ConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class MachinePatternTerminalBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : AENetworkBlockEntity(type, pos, state), IPatternTerminalMenuHost, IPatternTerminalLogicHost {

    private val configManager = ConfigManager { }
    private val encodingLogic = MachinePatternEncodingLogic(this)

    override fun getLogic(): PatternEncodingLogic = encodingLogic

    override fun getInventory(): MEStorage? {
        val grid = mainNode.grid
        return grid?.storageService?.inventory
    }

    override fun getConfigManager(): IConfigManager = configManager

    override fun getUpgrades(): IUpgradeInventory =
        UpgradeInventories.forMachine(AEBlocks.PATTERN_PROVIDER, 0) { }

    override fun returnToMainMenu(player: Player, subMenu: ISubMenu) {
        MenuOpener.open(MachinePatternTerminalMenu.TYPE, player, subMenu.locator, true)
    }

    override fun getMainMenuIcon(): ItemStack =
        ItemStack(AEBlocks.PATTERN_PROVIDER)

    override fun getLevel(): net.minecraft.world.level.Level = level!!

    override fun markForSave() {
        setChanged()
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        encodingLogic.writeToNBT(tag)
    }

    override fun loadTag(tag: CompoundTag) {
        super.loadTag(tag)
        encodingLogic.readFromNBT(tag)
    }
}
