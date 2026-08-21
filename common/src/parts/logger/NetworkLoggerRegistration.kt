package allyouneed.parts.logger

import net.minecraft.world.level.block.entity.BlockEntityType

object NetworkLoggerRegistration {
    private var type: BlockEntityType<*>? = null

    fun setBlockEntityType(t: BlockEntityType<*>) {
        type = t
    }

    fun getBlockEntityType(): BlockEntityType<*> =
        type ?: throw IllegalStateException("Network logger BE type not registered yet")
}
