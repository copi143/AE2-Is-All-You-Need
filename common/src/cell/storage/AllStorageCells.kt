package allyouneed.cell.storage

import allyouneed.logic.aekey.*
import allyouneed.util.WithEntries
import appeng.api.stacks.AEKeyType

/** Registry of every storage cell group, keyed by key-type id (mirrors the resgen groups). */
object AllStorageCells {
    /** Every storage cell definition, flattened for registration and client tinting. */
    val entries: List<TypedStorageCell> = listOf(
        Item,
        Fluid,
        Energy,
        Mana,
        Hp,
        Sta,
        Xp,
    ).flatMap { it.entries }

    val groups: Map<String, List<TypedStorageCell>> = entries.groupBy { it.label.lowercase() }

    /**
     * Item storage cells from 1K to 256T, following the vanilla
     * `item_storage_cell` design but with long-based capacity.
     */
    class Item(size: Long = -1) : TypedStorageCell(size, "Item", AEKeyType.items()) {
        companion object : WithEntries<Item> {
            override val entries = sizeList.map { Item(it) }
        }
    }

    /**
     * Fluid storage cells, mirroring the vanilla `fluid_storage_cell` design but with
     * long-based capacity (18 types max per cell, like vanilla).
     */
    class Fluid(size: Long = -1) : TypedStorageCell(size, "Fluid", AEKeyType.fluids()) {
        companion object : WithEntries<Fluid> {
            override val entries = sizeList.map { Fluid(it) }
        }
    }

    /** Energy storage cells, storing AE energy as a resource (6 types max per cell). */
    class Energy(size: Long = -1) : TypedStorageCell(size, "Energy", EnergyKey.Type) {
        companion object : WithEntries<Energy> {
            override val entries = sizeList.map { Energy(it) }
        }
    }

    /**
     * Mana storage cells from 1K to 256T, following the `item_storage_cell` design.
     * Each byte holds [allyouneed.logic.aekey.ManaKey.MANA_PER_BYTE] AM (the AE2 mana unit);
     * every mana system is stored as its own key (e.g. Botania mana at 1 Mana = 5 AM).
     */
    class Mana(size: Long = -1) : TypedStorageCell(size, "Mana", ManaKey.Type) {
        companion object : WithEntries<Mana> {
            override val entries = sizeList.map { Mana(it) }
        }
    }

    /** Health storage cells (3 types max per cell). */
    class Hp(size: Long = -1) : TypedStorageCell(size, "HP", HpKey.Type) {
        companion object : WithEntries<Hp> {
            override val entries = sizeList.map { Hp(it) }
        }
    }

    /** Hunger storage cells (3 types max per cell). */
    class Sta(size: Long = -1) : TypedStorageCell(size, "STA", StaKey.Type) {
        companion object : WithEntries<Sta> {
            override val entries = sizeList.map { Sta(it) }
        }
    }

    /** Experience storage cells (3 types max per cell). */
    class Xp(size: Long = -1) : TypedStorageCell(size, "XP", XpKey.Type) {
        companion object : WithEntries<Xp> {
            override val entries = sizeList.map { Xp(it) }
        }
    }
}
