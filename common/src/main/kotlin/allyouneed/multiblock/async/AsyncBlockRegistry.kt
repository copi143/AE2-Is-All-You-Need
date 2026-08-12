package allyouneed.multiblock.async

import net.minecraft.world.level.block.Block

/**
 * 从 async 方块种类到实际注册方块的查找表。结构方块由各平台初始化注册
 * （ForgeBlocks / FabricBlocks）；GT 所有的种类（控制器、连接器）由
 * GTAsyncCrafting 在 GTCEu 加载后注册。GT 的组模式通过这些映射解析方块。
 *
 * Lookup from async block kind to the actual registered block. Structural kinds are registered by
 * the platform init (ForgeBlocks / FabricBlocks); the GT-owned kinds (controllers, connectors) are
 * registered by GTAsyncCrafting when GTCEu is loaded. GT patterns resolve blocks through this map.
 */
object AsyncBlockRegistry {
    private val blocks = HashMap<AsyncBlockKind, Block>()

    fun register(kind: AsyncBlockKind, block: Block) {
        blocks[kind] = block
    }

    fun get(kind: AsyncBlockKind): Block? = blocks[kind]
}
