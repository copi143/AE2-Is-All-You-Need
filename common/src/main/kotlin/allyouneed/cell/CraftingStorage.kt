package allyouneed.cell

import allyouneed.util.rl
import allyouneed.util.*
import appeng.block.AEBaseBlockItem
import appeng.block.AEBaseEntityBlock
import appeng.block.crafting.CraftingUnitBlock
import appeng.block.crafting.ICraftingUnitType
import appeng.blockentity.AEBaseBlockEntity
import appeng.blockentity.crafting.CraftingBlockEntity
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import com.mojang.datafixers.types.Type
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

enum class CraftingStorage(size: Double = -1.0) : ICraftingUnitType {
    Micro(1.0.Ki), //
    Simple(4.0.Ki), //
    Basic(16.0.Ki), //
    Normal(64.0.Ki), //
    Enhanced(256.0.Ki), //
    Advanced(1.0.Mi), //
    Reinforced(4.0.Mi), //
    Dense(16.0.Mi), //
    Hyper(64.0.Mi), //
    Ultra(256.0.Mi), //
    Ultimate(1.0.Gi), //
    Singular(4.0.Gi), //
    Quantum(16.0.Gi), //
    Stellar(64.0.Gi), //
    Cosmic(256.0.Gi), //
    T1(1.0.Ti), //
    T4(4.0.Ti), //
    T16(16.0.Ti), //
    T64(64.0.Ti), //
    T256(256.0.Ti), //
    Creative; //

    private val prefix = if (size < 0) {
        null
    } else {
        assert(size.toBits() and 0x00fffff_ffffffff == 0L)
        formatScaledUnit(size.floatingExp)
    }

    val blockName: String = (prefix?.uppercase() ?: "Creative") + " Crafting Storage"
    val isCreative: Boolean = size < 0

    /** Capacity in bytes for non-creative tiers (exact power-of-two). */
    private val sizeBytes: Long = if (size > 0) size.toLong() else -1L

    val blockId: ResourceLocation = ((prefix ?: "creative") + "_crafting_storage").rl

    val blockSupplier = Supplier<Block> { CraftingUnitBlock(this) }

    val define: BlockDefinition<Block> = run {
        val block = blockSupplier.get()
        val item: BlockItem = if (block is appeng.block.AEBaseBlock) {
            AEBaseBlockItem(block, Item.Properties())
        } else {
            BlockItem(block, Item.Properties())
        }
        BlockDefinition(blockName, blockId, block, item).apply {
            MainCreativeTab.add(this)
        }
    }

    override fun getStorageBytes(): Long = if (isCreative) Long.MAX_VALUE else sizeBytes

    /**
     * Full capacity for BigInteger CPU accounting.
     * Creative is unbounded (null marker via [isCreative]).
     */
    fun getStorageBytesBig(): BigInteger =
        if (isCreative) BigInteger.valueOf(Long.MAX_VALUE) else BigInteger.valueOf(sizeBytes)

    override fun getAcceleratorThreads(): Int = 0

    override fun getItemFromType(): Item = define.asItem()

    companion object {
        lateinit var blockEntityType: BlockEntityType<CraftingBlockEntity>
            private set

        private var registered = false

        @Suppress("UNCHECKED_CAST")
        fun registerBEType() {
            if (registered) return
            registered = true

            val blocks = entries.map { it.define.block() }.toTypedArray()
            val typeRef = AtomicReference<BlockEntityType<CraftingBlockEntity>>()
            typeRef.set(
                BlockEntityType.Builder.of(
                    { pos, state -> CraftingBlockEntity(typeRef.get(), pos, state) },
                    *blocks,
                ).build(null as Type<*>?),
            )
            blockEntityType = typeRef.get()

            for (block in blocks) {
                require(block is AEBaseEntityBlock<*>) { "Crafting storage must be AEBaseEntityBlock" }
                (block as AEBaseEntityBlock<CraftingBlockEntity>).setBlockEntity(
                    CraftingBlockEntity::class.java,
                    blockEntityType,
                    null,
                    null,
                )
            }

            for (entry in entries) {
                AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, entry.define.asItem())
            }
        }
    }
}
