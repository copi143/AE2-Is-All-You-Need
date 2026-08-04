package allyouneed.async

import net.minecraft.world.level.block.entity.BlockEntityType

/**
 * 跨平台保存 async 合成结构所需的两个方块实体类型：结构控制器/接口的 BE 类型
 * 与结构连接器的 BE 类型。各平台注册 BE 时通过 [setStructureBlockEntityType] /
 * [setStructureConnectorBlockEntityType] 注入，供需要按 BE 类型查找的逻辑使用。
 */
object AsyncCraftingRegistration {
    private var structureBlockEntityType: BlockEntityType<*>? = null
    private var structureConnectorBlockEntityType: BlockEntityType<*>? = null

    /** async 合成结构控制器/接口的方块实体类型。 / Block entity type of the async synthesis structure controllers/interfaces. */
    fun setStructureBlockEntityType(t: BlockEntityType<*>) {
        structureBlockEntityType = t
    }

    /** async 合成结构连接器的方块实体类型。 / Block entity type of the async synthesis structure connectors. */
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
