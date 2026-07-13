package allyouneed.pattern

import appeng.api.stacks.AEKey
import appeng.api.stacks.GenericStack

/**
 * Small re-implementation of AE2's internal condense logic so we don't depend on package-private classes.
 */
object AEPatternUtil {
    fun condenseStacks(sparseInput: Array<GenericStack?>): Array<GenericStack> {
        val map = linkedMapOf<AEKey, Long>()
        for (input in sparseInput) {
            if (input != null) {
                map.merge(input.what(), input.amount(), Long::plus)
            }
        }
        if (map.isEmpty()) {
            return emptyArray()
        }
        return map.map { (k, v) -> GenericStack(k, v) }.toTypedArray()
    }
}
