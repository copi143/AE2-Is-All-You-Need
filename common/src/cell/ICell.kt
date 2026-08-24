package allyouneed.cell

import allyouneed.util.*
import appeng.api.stacks.AEKeyType
import appeng.block.AEBaseBlock
import appeng.block.AEBaseBlockItem
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import appeng.core.definitions.ItemDefinition
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.math.BigInteger
import java.util.function.BiFunction
import java.util.function.Supplier

/**
 * 对于有限的 [size] 必须为 2 的幂
 * 对于无限大小的，[size] 设置为 0
 * 对于内部内容能够凭空无限提取的，[size] 设置为 -1
 */
sealed class ICell(val size: Long) {
    init {
        if (size > 0) {
            require(size and (size - 1) == 0L) { "Size must be power of 2" }
        } else if (size < -1) {
            throw IllegalArgumentException("Size must be >0 or 0 or -1")
        }
    }

    val isCreative: Boolean = size == -1L
    val isUnlimited: Boolean = size == 0L
    val isLimited: Boolean = size > 0L

    /** 以 [BigInteger] 表示的 [size]，如果不限大小，则为 null */
    val sizeBig: BigInteger? = if (size > 0) BigInteger.valueOf(size) else null

    /** 将 [size] 表示为 2^N */
    val sizeExp: Int = if (size > 0) size.countTrailingZeroBits() else -1

    val prefix: String? = if (size > 0) formatScaledUnit(sizeExp) else null
    open val prefixLower: String = prefix?.lowercase() ?: when {
        isCreative -> "creative"
        isUnlimited -> "unlimited"
        else -> throw IllegalStateException("Unsupported cell size: $size")
    }
    open val prefixUpper: String = prefix?.uppercase() ?: when {
        isCreative -> "Creative"
        isUnlimited -> "Unlimited"
        else -> throw IllegalStateException("Unsupported cell size: $size")
    }

    /**
     * Bytes reserved per distinct item type.
     * AE2 scales this with tier: 8 bytes per KiB.
     */
    open val bytesPerType: Long = size / 1024 * 8

    /**
     * 对于存储单种 AEKey 的元件，直接设置其值
     * 对于存储多种 AEKey 的元件，设置为主类型，如果没有主类型则设置为 null
     */
    open val keyType: AEKeyType? = null

    /**
     * 对于存储多种 AEKey 的元件，在此处列出所有 AEKeyType
     * 注意此列表可以为空列表，此时表示可以存储的 AEKeyType 按照具体源码过滤
     */
    open val keyTypes: List<AEKeyType> by lazy { if (keyType == null) emptyList() else listOf(keyType!!) }

    /**
     * 每个字节最大会存储多少数量的内容，对于能存储多种不同 AEKey 的元件，这个值取多个 AEKey 中最大的
     */
    open val maxAmountPerByte: BigInteger by lazy {
        BigInteger.valueOf((keyTypes.maxOfOrNull { it.amountPerByte } ?: defaultAmountPerByte).toLong())
    }

    /**
     * 整个元件最大可能存储多少单种 AEKey 的内容，这可以被用来判定是否需要使用 [BigInteger]
     * 数量如果无限则设置为 null
     */
    open val maxAmount: BigInteger? by lazy { sizeBig?.multiply(maxAmountPerByte) }

    /**
     * Whether this cell's max amount (`size * amountPerByte`) overflows `Long`.
     * `true` → use BigInteger inventory, `false` → use fast long inventory.
     */
    open val requiresBigInt: Boolean by lazy {
        maxAmount == null || maxAmount!! > BigInteger.valueOf(Long.MAX_VALUE)
    }

    /**
     * Idle energy drain in AE/t, scaling 0.5 per 4x tier like vanilla.
     */
    open val idleDrain: Double = if (sizeExp < 0) 64.0 else 0.5 + 0.5 * ((sizeExp - 10) / 2)

    companion object {
        val sizeList: List<Long> = listOf(
            1L.Ki, 4L.Ki, 16L.Ki, 64L.Ki, 256L.Ki,
            1L.Mi, 4L.Mi, 16L.Mi, 64L.Mi, 256L.Mi,
            1L.Gi, 4L.Gi, 16L.Gi, 64L.Gi, 256L.Gi,
            1L.Ti, 4L.Ti, 16L.Ti, 64L.Ti, 256L.Ti,
        )

        /**
         * 每字节 8 个最小单元，这是 AE 的默认值
         */
        const val defaultAmountPerByte: Int = 8
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

    abstract override val keyType: AEKeyType

    /**
     * AE2 parity: allows fine-tuned blacklisting per cell. Mirrors
     * [appeng.api.storage.cells.IBasicCellItem.isBlackListed].
     */
    open fun isBlackListed(stack: ItemStack, key: appeng.api.stacks.AEKey): Boolean = false

    abstract val define: ItemDefinition<*>
}
