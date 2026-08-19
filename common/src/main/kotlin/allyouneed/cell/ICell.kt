package allyouneed.cell

import allyouneed.util.*
import appeng.core.definitions.BlockDefinition
import appeng.core.definitions.ItemDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import java.util.function.Supplier

abstract class ICell(val size: Long) {
    val isCreative: Boolean = size < 0

    val sizeExp: Int = if (size < 0) -1 else size.countTrailingZeroBits()

    val prefix: String? = if (size < 0) null else formatScaledUnit(sizeExp)
    val prefixLower: String = prefix ?: "creative"
    val prefixUpper: String = prefix?.uppercase() ?: "Creative"

    /**
     * Bytes reserved per distinct item type.
     * AE2 scales this with tier: 8 bytes per KiB.
     */
    val bytesPerType: Long = size / 1024 * 8

    /**
     * 每字节 8 个最小单元
     */
    val maxAmounts: Long = size * 8

    /**
     * Idle energy drain in AE/t, scaling 0.5 per 4x tier like vanilla.
     */
    val idleDrain: Double = 0.5 + 0.5 * ((sizeExp - 10) / 2)

    companion object {
        val sizeList: List<Long> = listOf(
            1L.Ki, 4L.Ki, 16L.Ki, 64L.Ki, 256L.Ki,
            1L.Mi, 4L.Mi, 16L.Mi, 64L.Mi, 256L.Mi,
            1L.Gi, 4L.Gi, 16L.Gi, 64L.Gi, 256L.Gi,
            1L.Ti, 4L.Ti, 16L.Ti, 64L.Ti, 256L.Ti,
        )
    }
}

abstract class ICellBlock(size: Long) : ICell(size) {
    abstract val blockName: String
    abstract val blockId: ResourceLocation
    abstract val blockSupplier: Supplier<Block>
    abstract val define: BlockDefinition<Block>
}

abstract class ICellItem(size: Long) : ICell(size) {
    abstract val itemName: String
    abstract val itemId: ResourceLocation
    abstract val define: ItemDefinition<*>
}
