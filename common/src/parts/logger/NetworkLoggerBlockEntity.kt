package allyouneed.parts.logger

import allyouneed.util.DismantleFlags
import appeng.api.networking.IGridNodeListener
import appeng.blockentity.grid.AENetworkBlockEntity
import appeng.util.SettingsFrom
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class NetworkLoggerBlockEntity(
    type: BlockEntityType<*>,
    pos: BlockPos,
    state: BlockState,
) : AENetworkBlockEntity(type, pos, state) {

    var loggerId: Int = 0
        private set

    var conflict: Boolean = false
        private set

    init {
        mainNode.setIdlePowerUsage(0.5)
    }

    override fun onReady() {
        super.onReady()
        ensureId()
    }

    override fun onMainNodeStateChanged(reason: IGridNodeListener.State) {
        markForUpdate()
    }

    fun ensureId() {
        if (loggerId != 0 || level?.isClientSide != false) return
        loggerId = LogStore.allocateId()
        if (loggerId != 0) {
            setChanged()
        }
    }

    fun record(entry: NetworkLogEntry) {
        ensureId()
        if (loggerId == 0) return
        LogStore.append(loggerId, entry)
    }

    fun setConflict(value: Boolean) {
        if (conflict == value) return
        conflict = value
        markForUpdate()
    }

    fun isOnline(): Boolean = mainNode.isOnline

    override fun loadTag(data: CompoundTag) {
        super.loadTag(data)
        loggerId = data.getInt(TAG_ID)
        conflict = data.getBoolean(TAG_CONFLICT)
    }

    override fun saveAdditional(data: CompoundTag) {
        super.saveAdditional(data)
        if (loggerId != 0) {
            data.putInt(TAG_ID, loggerId)
        }
        if (conflict) {
            data.putBoolean(TAG_CONFLICT, true)
        }
    }

    override fun exportSettings(mode: SettingsFrom, output: CompoundTag, player: Player?) {
        super.exportSettings(mode, output, player)
        if (mode == SettingsFrom.DISMANTLE_ITEM && loggerId != 0 && DismantleFlags.isWrenchDismantling()) {
            output.putInt(TAG_ID, loggerId)
        }
    }

    override fun importSettings(mode: SettingsFrom, input: CompoundTag, player: Player?) {
        super.importSettings(mode, input, player)
        if (mode == SettingsFrom.DISMANTLE_ITEM && input.contains(TAG_ID)) {
            loggerId = input.getInt(TAG_ID)
        }
    }

    override fun setRemoved() {
        val id = loggerId
        val dropStore = level?.isClientSide == false && id != 0 && !DismantleFlags.isWrenchDismantling()
        super.setRemoved()
        if (dropStore) {
            LogStore.delete(id)
        }
    }

    companion object {
        const val TAG_ID = "lid"
        const val TAG_CONFLICT = "conflict"
    }
}
