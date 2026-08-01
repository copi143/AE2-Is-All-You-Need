package allyouneed.async

import net.minecraft.world.level.block.entity.BlockEntityType

object AsyncCraftingRegistration {
    private var unitBlockEntityType: BlockEntityType<*>? = null
    private var connectorBlockEntityType: BlockEntityType<*>? = null

    fun setUnitBlockEntityType(t: BlockEntityType<*>) {
        unitBlockEntityType = t
    }

    fun setConnectorBlockEntityType(t: BlockEntityType<*>) {
        connectorBlockEntityType = t
    }

    fun getBlockEntityType(role: AsyncCraftingUnitRole): BlockEntityType<*> {
        return if (role == AsyncCraftingUnitRole.CONNECTOR) {
            connectorBlockEntityType
                ?: throw IllegalStateException("Async connector BE type not registered yet")
        } else {
            unitBlockEntityType
                ?: throw IllegalStateException("Async unit BE type not registered yet")
        }
    }
}
