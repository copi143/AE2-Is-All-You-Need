package allyouneed.machineassembler

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * Holds the block and block-entity type to avoid circular dependencies.
 */
object MachineAssemblerRegistration {
    private var type: BlockEntityType<*>? = null
    private var blockRef: Block? = null

    fun setBlockEntityType(t: BlockEntityType<*>) {
        type = t
    }

    fun getBlockEntityType(): BlockEntityType<*> {
        return type ?: throw IllegalStateException("Machine assembler BE type not registered yet")
    }

    fun setBlock(b: Block) {
        blockRef = b
    }

    val block: Block
        get() = blockRef ?: throw IllegalStateException("Machine assembler block not registered yet")
}
