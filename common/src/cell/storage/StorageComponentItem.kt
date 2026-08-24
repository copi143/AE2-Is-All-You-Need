package allyouneed.cell.storage

import appeng.api.implementations.items.IStorageComponent
import appeng.items.AEBaseItem
import net.minecraft.world.item.ItemStack

/**
 * Storage component item, mirrors AE2's [appeng.items.materials.StorageComponentItem].
 * Stores the tier's total bytes as [bytes] (long to support 256T). [getBytes] is capped to
 * int range for [IStorageComponent] compatibility; bytes are always power-of-two (2^10 .. 2^48).
 */
class StorageComponentItem(
    properties: Properties,
    val bytes: Long,
) : AEBaseItem(properties), IStorageComponent {

    override fun getBytes(stack: ItemStack): Int {
        // AE2's API is int-based (max 256K = 262144 fits); for larger tiers saturate to the largest
        // power-of-two that fits in int (1<<30 = 1073741824, multiple of 8) to keep power-of-two invariant.
        if (bytes <= Int.MAX_VALUE) return bytes.toInt()
        // highest power of two <= Integer.MAX_VALUE (which itself is not power-of-two)
        return 1 shl 30 // 1073741824
    }

    override fun isStorageComponent(stack: ItemStack): Boolean = true
}
