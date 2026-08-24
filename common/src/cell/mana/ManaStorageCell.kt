package allyouneed.cell.mana

import allyouneed.cell.storage.TypedStorageCell
import allyouneed.logic.aekey.ManaKey

/**
 * Mana storage cells from 1K to 256T, following the `item_storage_cell` design.
 * Each byte holds [allyouneed.logic.aekey.ManaKey.MANA_PER_BYTE] AM (the AE2 mana unit);
 * every mana system is stored as its own key (e.g. Botania mana at 1 Mana = 5 AM).
 */
class ManaStorageCell(size: Long = -1) : TypedStorageCell(size, "Mana Storage Cell", "Mana Cell", ManaKey.Type) {
    companion object {
        val entries = sizeList.map { ManaStorageCell(it) }
    }
}
