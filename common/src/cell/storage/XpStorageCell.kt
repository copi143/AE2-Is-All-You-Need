package allyouneed.cell.storage

import allyouneed.logic.aekey.XpKey

/** Experience storage cells (3 types max per cell). */
class XpStorageCell(size: Long = -1) : TypedStorageCell(size, "XP Storage Cell", "XP Cell", XpKey.Type) {
    companion object {
        val entries = sizeList.map { XpStorageCell(it) }
    }
}
