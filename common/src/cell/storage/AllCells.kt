package allyouneed.cell.storage

import allyouneed.cell.item.ItemStorageCell
import allyouneed.cell.mana.ManaStorageCell

/** Registry of every storage cell group, keyed by key-type id (mirrors the resgen groups). */
object AllCells {
    val groups: Map<String, List<TypedStorageCell>> = linkedMapOf(
        "item" to ItemStorageCell.entries,
        "fluid" to FluidStorageCell.entries,
        "mana" to ManaStorageCell.entries,
        "energy" to EnergyStorageCell.entries,
        "hp" to HpStorageCell.entries,
        "sta" to StaStorageCell.entries,
        "xp" to XpStorageCell.entries,
    )

    /** Every storage cell definition, flattened for registration and client tinting. */
    val all: List<TypedStorageCell> = groups.values.flatten()
}
