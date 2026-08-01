package allyouneed.pattern.machine

import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * Holder for the Machine Pattern Terminal BlockEntityType to avoid circular dependencies.
 */
object MachinePatternTerminalRegistration {
    private var type: BlockEntityType<*>? = null

    fun setBlockEntityType(t: BlockEntityType<*>) {
        type = t
    }

    fun getBlockEntityType(): BlockEntityType<*> {
        return type ?: throw IllegalStateException("MachinePatternTerminal BE type not registered yet")
    }
}
