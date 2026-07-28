package allyouneed.pattern.adaptive

import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * Holder for the Adaptive Pattern Terminal BlockEntityType to avoid circular dependencies.
 */
object AdaptivePatternTerminalRegistration {
    private var type: BlockEntityType<*>? = null

    fun setBlockEntityType(t: BlockEntityType<*>) {
        type = t
    }

    fun getBlockEntityType(): BlockEntityType<*> {
        return type ?: throw IllegalStateException("AdaptivePatternTerminal BE type not registered yet")
    }
}
