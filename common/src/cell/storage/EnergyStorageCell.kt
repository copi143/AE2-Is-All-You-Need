package allyouneed.cell.storage

import allyouneed.logic.aekey.EnergyKey

/** Energy storage cells, storing AE energy as a resource (6 types max per cell). */
class EnergyStorageCell(size: Long = -1) : TypedStorageCell(size, "Energy Storage Cell", "Energy Cell", EnergyKey.Type) {
    companion object {
        val entries = sizeList.map { EnergyStorageCell(it) }
    }
}
