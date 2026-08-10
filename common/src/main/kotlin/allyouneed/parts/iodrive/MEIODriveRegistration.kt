package allyouneed.parts.iodrive

import net.minecraft.world.level.block.entity.BlockEntityType

object MEIODriveRegistration {
    private var type: BlockEntityType<*>? = null

    fun setBlockEntityType(t: BlockEntityType<*>) {
        type = t
    }

    fun getBlockEntityType(): BlockEntityType<*> =
        type ?: throw IllegalStateException("MEIODrive BE type not registered yet")
}
