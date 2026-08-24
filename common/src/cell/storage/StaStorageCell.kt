package allyouneed.cell.storage

import allyouneed.logic.aekey.StaKey

/** Hunger storage cells (3 types max per cell). */
class StaStorageCell(size: Long = -1) : TypedStorageCell(size, "STA Storage Cell", "STA Cell", StaKey.Type) {
    companion object {
        val entries = sizeList.map { StaStorageCell(it) }
    }
}
