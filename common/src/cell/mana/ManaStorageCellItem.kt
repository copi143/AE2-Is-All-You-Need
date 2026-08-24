package allyouneed.cell.mana

import allyouneed.cell.item.ResourceCellItem

/**
 * Item form of a [ManaStorageCell]. Shares the cell-workbench plumbing with item cells;
 * contents live in a [ManaStorageCellInventory] keyed by [allyouneed.logic.aekey.ManaKey].
 */
class ManaStorageCellItem(
    properties: Properties,
    cellType: ManaStorageCell,
) : ResourceCellItem(properties, cellType)
