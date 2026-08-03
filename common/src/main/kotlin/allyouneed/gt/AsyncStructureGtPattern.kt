package allyouneed.gt

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition
import com.gregtechceu.gtceu.api.pattern.BlockPattern
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern
import com.gregtechceu.gtceu.api.pattern.Predicates

/**
 * Placeholder GT pattern for the async synthesis controllers. The real structure shape lives in the
 * common [allyouneed.async.AsyncStructureDetector] (the switch/processor cannot be expressed as a
 * GT pattern: a pattern repeats a single aisle only, while the extension bands are six interleaved
 * rows). [allyouneed.gt.AsyncStructureGtControllerMachine.checkPattern] overrides the pattern check
 * entirely, so this only needs to be constructible to satisfy registrate's non-null pattern check.
 */
fun placeholderAsyncStructurePattern(definition: MultiblockMachineDefinition): BlockPattern =
    FactoryBlockPattern.start()
        .aisle("C")
        .where('C', Predicates.controller(Predicates.blocks(definition.getBlock())))
        .build()
