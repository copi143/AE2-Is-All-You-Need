package allyouneed.gt

import allyouneed.async.AsyncBlockKind
import allyouneed.async.AsyncBlockRegistry
import allyouneed.multiblock.AsyncStructureType
import allyouneed.multiblock.AsyncStructures
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition
import com.gregtechceu.gtceu.api.pattern.BlockPattern
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern
import com.gregtechceu.gtceu.api.pattern.Predicates
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate
import net.minecraft.world.level.block.Block

/**
 * GTCEu pattern of an async synthesis structure, generated from the hand-written shape constants in
 * [AsyncStructures].
 *
 * For switch/processor this is the *forming* pattern: the trailing extension bays are encoded as one
 * "group" of [AsyncStructures.EXTENSION_DEPTH] consecutive aisles repeated `[0, MAX_EXTENSIONS]`
 * times via the [IGroupedBlockPattern] mixin, so a native [BlockPattern] check accepts 0..16 bays
 * exactly like the detector does (see [AsyncStructures.depth]). The module (factory) keeps a single
 * static 5-aisle pattern; the factory is formed by interface probing, not a pattern check.
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
     * Per-cell MACHINE predicates mirroring [AsyncStructures.isValidCell]:
     *  - 'M': floor machines, glass may not replace them;
     *  - 'R': machines on walls, glass may replace them;
     *  - 'V': switch core machines, wan/lan connectors may replace them (limits 1/2);
     *  - 'O': processor outer-shell machines, me/lan connectors may replace them (limits 1/2).
     */
    private const val GLASS_REPLACE_CHAR = 'R'
    private const val SWITCH_CORE_CHAR = 'V'
    private const val PROCESSOR_SHELL_CHAR = 'O'

    /**
     * Builds the full-depth structure of [type] as a GT pattern (base + one bay group + closing
     * row + back wall). The generator runs lazily (the pattern factory is memoized), so
     * [definition.block] and the [AsyncBlockRegistry] lookups are safe: by the first preview/JEI
     * access the GT block/item registrations have completed and `FMLCommonSetupEvent` has populated
     * the registry.
     */
    fun build(type: AsyncStructureType, definition: MultiblockMachineDefinition): BlockPattern {
        val builder = FactoryBlockPattern.start()
        for (z in 0 until AsyncStructures.depth(type, 1)) {
            val rows = Array(AsyncStructures.height(type)) { y ->
                CharArray(AsyncStructures.width(type)) { x -> cellChar(type, x, y, z) }.concatToString()
            }
            builder.aisle(*rows)
        }

        for ((kind, char) in KIND_TO_CHAR) {
            val block = AsyncBlockRegistry.get(kind) ?: continue
            builder.where(char, Predicates.blocks(block))
        }
        registerVariantPredicates(builder, type)
        builder.where(AIR_CHAR, Predicates.air())

        val controllerKind = cellKind(type)
        builder.where(
            KIND_TO_CHAR.getValue(controllerKind),
            Predicates.controller(Predicates.blocks(definition.block)),
        )
        val pattern = builder.build()

        if (type != AsyncStructureType.MODULE) {
            (pattern as IGroupedBlockPattern).setGroup(type.baseDepth - 2, AsyncStructures.EXTENSION_DEPTH)
        }
        return pattern
    }

    private fun registerVariantPredicates(builder: FactoryBlockPattern, type: AsyncStructureType) {
        val machine = requireNotNull(AsyncBlockRegistry.get(AsyncBlockKind.MACHINE)) { "missing MACHINE block" }
        val glass = requireNotNull(AsyncBlockRegistry.get(AsyncBlockKind.GLASS)) { "missing GLASS block" }
        builder.where(GLASS_REPLACE_CHAR, Predicates.blocks(machine).or(Predicates.blocks(glass)))
        when (type) {
            AsyncStructureType.SWITCH -> {
                val wan = requireNotNull(AsyncBlockRegistry.get(AsyncBlockKind.WAN_CONNECTOR)) { "missing WAN_CONNECTOR" }
                val lan = requireNotNull(AsyncBlockRegistry.get(AsyncBlockKind.LAN_CONNECTOR)) { "missing LAN_CONNECTOR" }
                builder.where(SWITCH_CORE_CHAR, corePredicate(machine, glass, wan, 1, lan, 2))
            }
            AsyncStructureType.PROCESSOR -> {
                val me = requireNotNull(AsyncBlockRegistry.get(AsyncBlockKind.ME_CONNECTOR)) { "missing ME_CONNECTOR" }
                val lan = requireNotNull(AsyncBlockRegistry.get(AsyncBlockKind.LAN_CONNECTOR)) { "missing LAN_CONNECTOR" }
                builder.where(PROCESSOR_SHELL_CHAR, corePredicate(machine, glass, me, 1, lan, 2))
            }
            AsyncStructureType.MODULE -> {}
        }
    }

    private fun corePredicate(
        machine: Block,
        glass: Block,
        connectorA: Block,
        maxA: Int,
        connectorB: Block,
        maxB: Int,
    ): TraceabilityPredicate = Predicates.blocks(machine).or(Predicates.blocks(glass))
        .or(Predicates.blocks(connectorA).setMaxGlobalLimited(maxA))
        .or(Predicates.blocks(connectorB).setMaxGlobalLimited(maxB))

    private fun cellChar(type: AsyncStructureType, x: Int, y: Int, z: Int): Char {
        if (AsyncStructures.isDonCare(type, x, y, z)) return DONTCARE_CHAR
        val kind = AsyncStructures.blockAt(type, 1, x, y, z) ?: return AIR_CHAR
        if (kind == AsyncBlockKind.MACHINE) return machineChar(type, x, y, z)
        return KIND_TO_CHAR.getValue(kind)
    }

    private fun machineChar(type: AsyncStructureType, x: Int, y: Int, z: Int): Char = when {
        AsyncStructures.isFloorCell(type, x, y, z) -> 'M'
        AsyncStructures.inCore(type, x, y, z) && type == AsyncStructureType.SWITCH -> SWITCH_CORE_CHAR
        AsyncStructures.inCore(type, x, y, z) &&
            type == AsyncStructureType.PROCESSOR &&
            AsyncStructures.isOuterShellCell(x, y, z) -> PROCESSOR_SHELL_CHAR
        else -> GLASS_REPLACE_CHAR
    }

    private fun cellKind(type: AsyncStructureType): AsyncBlockKind {
        val (ax, ay, az) = AsyncStructures.anchorCell(type)
        return checkNotNull(AsyncStructures.blockAt(type, 0, ax, ay, az)) { "anchor cell must be a controller" }
    }
}
