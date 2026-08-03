package allyouneed.async

import net.minecraft.world.level.block.Block

/**
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
