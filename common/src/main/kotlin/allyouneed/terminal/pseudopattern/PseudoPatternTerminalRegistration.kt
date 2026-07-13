package allyouneed.terminal

import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * Small holder so the Block can ask for the registered BlockEntityType without circular dependency.
 */
object PseudoPatternTerminalRegistration {
    private var type: BlockEntityType<*>? = null

    fun setBlockEntityType(t: BlockEntityType<*>) {
        type = t
    }

    fun getBlockEntityType(): BlockEntityType<*> {
        return type ?: throw IllegalStateException("PseudoPatternTerminal BE type not registered yet")
    }
}
