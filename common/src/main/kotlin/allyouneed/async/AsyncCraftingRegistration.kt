package allyouneed.async

import net.minecraft.world.level.block.entity.BlockEntityType

object AsyncCraftingRegistration {
    private var structureBlockEntityType: BlockEntityType<*>? = null
    private var structureConnectorBlockEntityType: BlockEntityType<*>? = null

    /** Block entity type of the async synthesis structure controllers/interfaces. */
    fun setStructureBlockEntityType(t: BlockEntityType<*>) {
        structureBlockEntityType = t
    }

    /** Block entity type of the async synthesis structure connectors. */
    fun setStructureConnectorBlockEntityType(t: BlockEntityType<*>) {
        structureConnectorBlockEntityType = t
    }

    fun getStructureBlockEntityType(): BlockEntityType<*> =
        structureBlockEntityType
            ?: throw IllegalStateException("Async structure BE type not registered yet")

    fun getStructureConnectorBlockEntityType(): BlockEntityType<*> =
        structureConnectorBlockEntityType
            ?: throw IllegalStateException("Async structure connector BE type not registered yet")
}
