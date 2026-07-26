package allyouneed.iodrive

import appeng.api.config.Actionable
import appeng.api.networking.security.IActionSource
import appeng.api.stacks.AEKey
import appeng.me.storage.DriveWatcher

class SelfFilteringDriveWatcher(
    cell: appeng.api.storage.cells.StorageCell,
    onActivity: Runnable,
    private val selfSource: IActionSource,
) : DriveWatcher(cell, onActivity) {

    override fun insert(what: AEKey, amount: Long, mode: Actionable, source: IActionSource): Long {
        if (source === selfSource) return 0
        return super.insert(what, amount, mode, source)
    }
}
