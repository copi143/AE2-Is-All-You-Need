package allyouneed.cell

import allyouneed.rl
import allyouneed.util.Gi
import allyouneed.util.Ki
import allyouneed.util.Mi
import allyouneed.util.Ti
import allyouneed.util.floatingExp
import allyouneed.util.formatScaledUnit
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

enum class CraftingStorage(name: String, size: Double = -1.0) : ICraftingUnitType {
    Micro("Micro Crafting Storage", 1.0.Ki),
    Simple("Simple Crafting Storage", 4.0.Ki),
    Basic("Basic Crafting Storage", 16.0.Ki),
    Normal("Normal Crafting Storage", 64.0.Ki),
    Enhanced("Enhanced Crafting Storage", 256.0.Ki),
    Advanced("Advanced Crafting Storage", 1.0.Mi),
    Reinforced("Reinforced Crafting Storage", 4.0.Mi),
    Dense("Dense Crafting Storage", 16.0.Mi),
    Hyper("Hyper Crafting Storage", 64.0.Mi),
    Ultra("Ultra Crafting Storage", 256.0.Mi),
    Ultimate("Ultimate Crafting Storage", 1.0.Gi),
    Singular("Singular Crafting Storage", 4.0.Gi),
    Quantum("Quantum Crafting Storage", 16.0.Gi),
    Stellar("Stellar Crafting Storage", 64.0.Gi),
    Cosmic("Cosmic Crafting Storage", 256.0.Gi),
    T1("1T Crafting Storage", 1.0.Ti),
    T4("4T Crafting Storage", 4.0.Ti),
    T16("16T Crafting Storage", 16.0.Ti),
    T64("64T Crafting Storage", 64.0.Ti),
    T256("256T Crafting Storage", 256.0.Ti),
    Creative("Creative Crafting Storage");

    val blockName: String = name
    val isCreative: Boolean = size < 0

    /** Capacity in bytes for non-creative tiers (exact power-of-two). */
    private val sizeBytes: Long = if (size > 0) size.toLong() else -1L

    val blockId: ResourceLocation = run {
        if (isCreative) {
            return@run name.lowercase().replace(" ", "_").rl
        }
        assert(size.toBits() and 0x00fffff_ffffffff == 0L)
        formatScaledUnit(size.floatingExp, "crafting_storage").rl
    }

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

    override fun getStorageBytes(): Long =
        if (isCreative) Long.MAX_VALUE else sizeBytes

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
