package allyouneed.gt

import allyouneed.async.AsyncBlockKind
import allyouneed.async.AsyncBlockRegistry
import allyouneed.multiblock.AsyncStructureType
import allyouneed.multiblock.AsyncStructures
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition
import com.gregtechceu.gtceu.api.pattern.BlockPattern
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern
import com.gregtechceu.gtceu.api.pattern.Predicates

/**
 * GTCEu pattern of an async synthesis structure, generated from the hand-written shape constants in
 * [AsyncStructures]. GTCEu needs a real [BlockPattern] so the sneak+empty-hand in-world preview
 * (and the JEI pattern page) can render the structure; forming itself is detector-driven and never
 * consults this pattern.
 *
 * The grid is authored in `FactoryBlockPattern.start()` coordinates: char (row string) = local x
 * (west->east), row = local y (bottom->top), aisle = local z (front->back), so `BlockPattern`
 * canonical (NORTH) output matches the detector's NORTH-facing world layout. This makes the preview
 * exact for NORTH/SOUTH-facing controllers; GTCEu's in-world renderer rotates EAST/WEST opposite to
 * any consistent facing convention, so those facings appear 180° rotated (upstream quirk that
 * affects every GT multiblock).
 */
object AsyncStructureGtPattern {

    private val KIND_TO_CHAR: Map<AsyncBlockKind, Char> = mapOf(
        AsyncBlockKind.FRAME to 'F',
        AsyncBlockKind.MACHINE to 'M',
        AsyncBlockKind.GLASS to 'G',
        AsyncBlockKind.TOWER to 'T',
        AsyncBlockKind.ENERGY to 'E',
        AsyncBlockKind.COMPUTING to 'C',
        AsyncBlockKind.STORAGE to 'S',
        AsyncBlockKind.EXECUTION to 'X',
        AsyncBlockKind.CONTROLLER to 'N',
        AsyncBlockKind.SWITCH to 'W',
        AsyncBlockKind.FACTORY to 'Y',
        AsyncBlockKind.CABLE to 'B',
        AsyncBlockKind.ME_CONNECTOR to '1',
        AsyncBlockKind.WAN_CONNECTOR to '2',
        AsyncBlockKind.LAN_CONNECTOR to '3',
        AsyncBlockKind.MODULE_INTERFACE to 'Z',
    )

    private const val AIR_CHAR = 'A'
    private const val DONTCARE_CHAR = ' '

    /**
     * Builds the base (extension 0) structure of [type] as a GT pattern. Every cell of the
     * hand-written shape becomes a concrete predicate: required cells match their block kind, cells
     * that must be air match air, and don't-care cells accept anything.
     *
     * The generator runs lazily (the pattern factory is memoized), so [definition.getBlock] and the
     * [AsyncBlockRegistry] lookups are safe: by the first preview/JEI access the GT block/item
     * registrations have completed and `FMLCommonSetupEvent` has populated the registry.
     */
    fun build(type: AsyncStructureType, definition: MultiblockMachineDefinition): BlockPattern {
        val builder = FactoryBlockPattern.start()
        val depth = AsyncStructures.depth(type, 0)
        for (z in 0 until depth) {
            val rows = Array(AsyncStructures.height(type)) { y ->
                CharArray(AsyncStructures.width(type)) { x -> cellChar(type, x, y, z) }.concatToString()
            }
            builder.aisle(*rows)
        }
        for ((kind, char) in KIND_TO_CHAR) {
            val block = AsyncBlockRegistry.get(kind) ?: continue
            builder.where(char, Predicates.blocks(block))
        }
        builder.where(AIR_CHAR, Predicates.air())
        val controllerKind = cellKind(type)
        builder.where(
            KIND_TO_CHAR.getValue(controllerKind),
            Predicates.controller(Predicates.blocks(definition.block)),
        )
        return builder.build()
    }

    private fun cellChar(type: AsyncStructureType, x: Int, y: Int, z: Int): Char {
        if (AsyncStructures.isDonCare(type, x, y, z)) return DONTCARE_CHAR
        val kind = AsyncStructures.blockAt(type, 0, x, y, z) ?: return AIR_CHAR
        return KIND_TO_CHAR.getValue(kind)
    }

    private fun cellKind(type: AsyncStructureType): AsyncBlockKind {
        val (ax, ay, az) = AsyncStructures.anchorCell(type)
        return checkNotNull(AsyncStructures.blockAt(type, 0, ax, ay, az)) { "anchor cell must be a controller" }
    }
}
