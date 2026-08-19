package allyouneed.cell

import allyouneed.util.rl
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

class CraftingStorage(size: Long = -1) : ICellBlock(size), ICraftingUnitType {
    override val blockName: String = "$prefixUpper Crafting Storage"

    override val blockId: ResourceLocation = "${prefixLower}_crafting_storage".rl

    override val blockSupplier = Supplier<Block> { CraftingUnitBlock(this) }

    override val define: BlockDefinition<Block> = run {
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

    override fun getStorageBytes(): Long = if (isCreative) Long.MAX_VALUE else size

    /**
     * Full capacity for BigInteger CPU accounting.
     * Creative is unbounded (null marker via [isCreative]).
     */
    fun getStorageBytesBig(): BigInteger =
        if (isCreative) BigInteger.valueOf(Long.MAX_VALUE) else BigInteger.valueOf(size)

    override fun getAcceleratorThreads(): Int = 0

    override fun getItemFromType(): Item = define.asItem()

    companion object {
        val entries = sizeList.map { CraftingStorage(it) } + CraftingStorage()

        lateinit var blockEntityType: BlockEntityType<CraftingBlockEntity>
            private set

        private var registered = false

        @Suppress("UNCHECKED_CAST")
        fun registerBEType() {
            if (registered) return
            registered = true

            val blocks = entries.map { it.define.block() }.toTypedArray()
            val typeRef = AtomicReference<BlockEntityType<CraftingBlockEntity>>()
            @Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS") typeRef.set(
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
