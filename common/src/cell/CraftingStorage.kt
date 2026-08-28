package allyouneed.cell

import allyouneed.util.interfaces.NeedRegisterBlockEntity
import appeng.block.AEBaseEntityBlock
import appeng.block.crafting.CraftingUnitBlock
import appeng.block.crafting.ICraftingUnitType
import appeng.blockentity.AEBaseBlockEntity
import appeng.blockentity.crafting.CraftingBlockEntity
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.math.BigInteger
import java.util.function.Supplier

class CraftingStorage(size: Long = -1) : ICellBlock(size, "Crafting Storage"), ICraftingUnitType,
    NeedRegisterBlockEntity {
    override val blockSupplier = Supplier<Block> { CraftingUnitBlock(this) }

    override fun getStorageBytes(): Long = if (isCreative) Long.MAX_VALUE else size

    /**
     * Full capacity for BigInteger CPU accounting.
     * Creative is unbounded (null marker via [isCreative]).
     */
    fun getStorageBytesBig(): BigInteger =
        if (isCreative) BigInteger.valueOf(Long.MAX_VALUE) else BigInteger.valueOf(size)

    override fun getAcceleratorThreads(): Int = 0

    override fun getItemFromType(): Item = define.asItem()

    @Suppress("UNCHECKED_CAST")
    override fun registerBlockEntity() {
        (define.block() as AEBaseEntityBlock<CraftingBlockEntity>).setBlockEntity(
            CraftingBlockEntity::class.java,
            blockEntityType,
            null,
            null,
        )
        AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, define.asItem())
    }

    @Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    companion object {
        val entries = sizeList.map { CraftingStorage(it) } + CraftingStorage()

        val blockEntityType: BlockEntityType<CraftingBlockEntity> by lazy {
            BlockEntityType.Builder.of(
                { pos, state -> CraftingBlockEntity(blockEntityType, pos, state) },
                *entries.map { it.define.block() }.toTypedArray(),
            ).build(null)
        }
    }
}
