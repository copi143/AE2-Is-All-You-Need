package allyouneed.async

import appeng.me.cluster.IAEMultiBlock
import net.minecraft.core.BlockPos

interface IAsyncCraftingBlockEntity : IAEMultiBlock<AsyncCraftingCPUCluster> {

    val unitType: AsyncCraftingUnitType

    fun updateStatus(cluster: AsyncCraftingCPUCluster?)

    fun updateMultiBlock(changedPos: BlockPos)
}
