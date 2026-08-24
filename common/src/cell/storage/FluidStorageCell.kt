package allyouneed.cell.storage

import appeng.api.stacks.AEKeyType

/**
 * Fluid storage cells, mirroring the vanilla `fluid_storage_cell` design but with
 * long-based capacity (18 types max per cell, like vanilla).
 */
class FluidStorageCell(size: Long = -1) : TypedStorageCell(size, "Fluid Storage Cell", "Fluid Cell", AEKeyType.fluids()) {
    companion object {
        val entries = sizeList.map { FluidStorageCell(it) }
    }
}
