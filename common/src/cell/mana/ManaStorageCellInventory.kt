package allyouneed.cell.mana

import allyouneed.cell.item.ItemStorageCellInventory
import allyouneed.logic.aekey.ManaKey
import appeng.api.storage.cells.ISaveProvider
import net.minecraft.world.item.ItemStack

/**
 * Mana variant of the long-based cell inventory: identical byte accounting,
 * but the key space is [ManaKey.Type] (all mana systems at once).
 */
class ManaStorageCellInventory(
    stack: ItemStack,
    container: ISaveProvider?,
) : ItemStorageCellInventory(stack, container, ManaKey.Type)
