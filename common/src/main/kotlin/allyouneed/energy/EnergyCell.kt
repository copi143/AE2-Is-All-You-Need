package allyouneed.energy

import allyouneed.rl
import allyouneed.util.Gi
import allyouneed.util.Ki
import allyouneed.util.Mi
import appeng.block.AEBaseBlock
import appeng.block.AEBaseBlockItem
import appeng.block.AEBaseEntityBlock
import appeng.block.networking.CreativeEnergyCellBlock
import appeng.block.networking.EnergyCellBlock
import appeng.block.networking.EnergyCellBlockItem
import appeng.blockentity.AEBaseBlockEntity
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity
import appeng.blockentity.networking.EnergyCellBlockEntity
import appeng.core.MainCreativeTab
import appeng.core.definitions.BlockDefinition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Supplier
import kotlin.math.pow

enum class EnergyCell(name: String, size: Double = -1.0) {
    Micro("Micro Energy Cell", 1.0.Ki), //
    Simple("Simple Energy Cell", 4.0.Ki), //
    Basic("Basic Energy Cell", 16.0.Ki), //
    Normal("Normal Energy Cell", 64.0.Ki), //
    Enhanced("Enhanced Energy Cell", 256.0.Ki), //
    Advanced("Advanced Energy Cell", 1.0.Mi), //
    Reinforced("Reinforced Energy Cell", 4.0.Mi), //
    Dense("Dense Energy Cell", 16.0.Mi), //
    Hyper("Hyper Energy Cell", 64.0.Mi), //
    Ultra("Ultra Energy Cell", 256.0.Mi), //
    Ultimate("Ultimate Energy Cell", 1.0.Gi), //
    Singular("Singular Energy Cell", 4.0.Gi), //
    Quantum("Quantum Energy Cell", 16.0.Gi), //
    Stellar("Stellar Energy Cell", 64.0.Gi), //
    Cosmic("Cosmic Energy Cell", 256.0.Gi), //
    Creative("Creative Energy Cell"); //

    val blockName: String = name

    val blockId: ResourceLocation = run {
        if (!(size > 0)) {
            return@run name.lowercase().replace(" ", "_").rl
        }
        assert(size.toBits() and 0x00fffff_ffffffff == 0L)
        val exp = (size.toBits() shr 52).toInt() - 1023
        when {
            exp >= 30 -> "${2.0.pow(exp - 30).toInt()}g_energy_cell".rl
            exp >= 20 -> "${2.0.pow(exp - 20).toInt()}m_energy_cell".rl
            exp >= 10 -> "${2.0.pow(exp - 10).toInt()}k_energy_cell".rl
            else -> "${2.0.pow(exp).toInt()}b_energy_cell".rl
        }
    }

    val blockSupplier = if (size < 0) {
        Supplier<Block> { CreativeEnergyCellBlock() }
    } else {
        Supplier<Block> { EnergyCellBlock(size * 64.0, size * 4.0, (size / 1024.0).toInt() * 100) }
    }

    val itemFactory = if (size < 0) null else BiFunction<Block, Item.Properties, BlockItem> { block, props ->
        EnergyCellBlockItem(block, props)
    }

    val define: BlockDefinition<Block> = run {
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

    lateinit var blockEntityType: BlockEntityType<*>
        private set

    @Suppress("UNCHECKED_CAST")
    fun registerBEType() {
        val block = define.block()
        require(block is AEBaseEntityBlock<*>) { "EnergyCell block must be AEBaseEntityBlock" }

        val typeRef = AtomicReference<BlockEntityType<*>>()

        if (this == Creative) {
            typeRef.set(BlockEntityType.Builder.of({ pos, state ->
                CreativeEnergyCellBlockEntity(
                    typeRef.get() as BlockEntityType<CreativeEnergyCellBlockEntity>, pos, state
                )
            }, block).build(null as com.mojang.datafixers.types.Type<*>?))
            blockEntityType = typeRef.get()
            (block as AEBaseEntityBlock<CreativeEnergyCellBlockEntity>).setBlockEntity(
                CreativeEnergyCellBlockEntity::class.java,
                blockEntityType as BlockEntityType<CreativeEnergyCellBlockEntity>,
                null,
                null,
            )
        } else {
            typeRef.set(BlockEntityType.Builder.of({ pos, state ->
                EnergyCellBlockEntity(typeRef.get() as BlockEntityType<EnergyCellBlockEntity>, pos, state)
            }, block).build(null as com.mojang.datafixers.types.Type<*>?))
            blockEntityType = typeRef.get()
            (block as AEBaseEntityBlock<EnergyCellBlockEntity>).setBlockEntity(
                EnergyCellBlockEntity::class.java,
                blockEntityType as BlockEntityType<EnergyCellBlockEntity>,
                null,
                null,
            )
        }

        AEBaseBlockEntity.registerBlockEntityItem(blockEntityType, define.asItem())
    }
}
