package allyouneed.cell.storage

import allyouneed.logic.aekey.HpKey

/** Health storage cells (3 types max per cell). */
class HpStorageCell(size: Long = -1) : TypedStorageCell(size, "HP Storage Cell", "HP Cell", HpKey.Type) {
    companion object {
        val entries = sizeList.map { HpStorageCell(it) }
    }
}
