package allyouneed.cell

import allyouneed.util.*
import appeng.block.AEBaseBlock
import appeng.block.AEBaseBlockItem
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import appeng.core.definitions.ItemDefinition
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.function.BiFunction
import java.util.function.Supplier

sealed class ICell(val size: Long) {
    val isCreative: Boolean = size < 0

    val sizeExp: Int = if (size < 0) -1 else size.countTrailingZeroBits()

    open val prefix: String? = if (size < 0) null else formatScaledUnit(sizeExp)
    open val prefixLower: String = prefix ?: "creative"
    open val prefixUpper: String = prefix?.uppercase() ?: "Creative"

    /**
     * Bytes reserved per distinct item type.
     * AE2 scales this with tier: 8 bytes per KiB.
     */
    open val bytesPerType: Long = size / 1024 * 8

    /**
     * 每字节 8 个最小单元
     */
    open val maxAmounts: Long = size * 8

    /**
     * Idle energy drain in AE/t, scaling 0.5 per 4x tier like vanilla.
     */
    open val idleDrain: Double = 0.5 + 0.5 * ((sizeExp - 10) / 2)

    companion object {
        val sizeList: List<Long> = listOf(
            1L.Ki, 4L.Ki, 16L.Ki, 64L.Ki, 256L.Ki,
            1L.Mi, 4L.Mi, 16L.Mi, 64L.Mi, 256L.Mi,
            1L.Gi, 4L.Gi, 16L.Gi, 64L.Gi, 256L.Gi,
            1L.Ti, 4L.Ti, 16L.Ti, 64L.Ti, 256L.Ti,
        )
    }
}

abstract class ICellBlock(size: Long, val postfix: String, protected val postfix2: String = postfix) : ICell(size) {
    open val blockName: String = "$prefixUpper $postfix"
    protected val blockName2: String = "$prefixUpper $postfix2"
    open val blockId: ResourceLocation = idify("$prefixUpper $postfix").rl
    protected val blockId2: ResourceLocation = idify("$prefixUpper $postfix2").rl
    abstract val blockSupplier: Supplier<Block>
    open val define: BlockDefinition<Block> by lazy {
        val block = blockSupplier.get()
        val item = itemFactory?.apply(block, Item.Properties()) ?: if (block is AEBaseBlock) {
            AEBaseBlockItem(block, Item.Properties())
        } else {
            BlockItem(block, Item.Properties())
        }
        BlockDefinition(blockName, blockId, block, item).apply {
            MainCreativeTab.add(this)
        }
    }
    open val itemFactory: BiFunction<Block, Item.Properties, BlockItem>? = null
    open val blockEntityFactory: BiFunction<BlockPos, BlockState, BlockEntity>? = null
}

abstract class ICellItem(size: Long, val postfix: String, protected val postfix2: String = postfix) : ICell(size) {
    open val itemName: String = "$prefixUpper $postfix"
    protected val itemName2: String = "$prefixUpper $postfix2"
    open val itemId: ResourceLocation = idify("$prefixUpper $postfix").rl
    protected val itemId2: ResourceLocation = idify("$prefixUpper $postfix2").rl
    abstract val define: ItemDefinition<*>
}
