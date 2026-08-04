package allyouneed.gt

/**
 * Marks a GTCEu [BlockPattern] as carrying "group repetition" metadata injected by the
 * `BlockPatternGroupedMixin`: an aisle may represent a whole group of [getGroupSize] consecutive
 * aisles (6 for the async extension bays) that is repeated as a unit instead of a single slice.
 *
 * [BlockPattern.aisleRepetitions] keeps its upstream meaning ([min, max] repeats of the step that
 * starts at the group's first aisle); the group interior aisles are matched contiguously as part of
 * that step and are never visited on their own. Aisle indexes that are not a group start report a
 * size of 1.
 */
interface IGroupedBlockPattern {

    /** Declares that the step starting at [aisleIndex] repeats [groupSize] consecutive aisles. */
    fun setGroup(aisleIndex: Int, groupSize: Int)

    /** Number of consecutive aisles matched by the step starting at [aisleIndex] (1 for singles). */
    fun getGroupSize(aisleIndex: Int): Int
}
