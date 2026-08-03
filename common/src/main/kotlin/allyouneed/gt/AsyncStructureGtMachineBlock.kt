package allyouneed.gt

import allyouneed.async.AsyncBlockKind
import allyouneed.async.IAsyncKindBlock
import com.gregtechceu.gtceu.api.block.MetaMachineBlock
import com.gregtechceu.gtceu.api.machine.MachineDefinition
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

/**
 * GT machine block of the async synthesis system (the three controllers and the three connectors).
 * Wraps a regular [MetaMachineBlock] and exposes the async block kind so the common structure
 * detectors read controllers/connectors from the world regardless of which registration path
 * produced them.
 */
class AsyncStructureGtMachineBlock(
    props: BlockBehaviour.Properties,
    definition: MachineDefinition,
    override val kind: AsyncBlockKind,
) : MetaMachineBlock(props, definition), IAsyncKindBlock {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        definition.getBlockEntityType().create(pos, state)
}
