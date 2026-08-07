package allyouneed.gtceu

/**
 * 把 GTCEu 的 [BlockPattern] 标记为携带“分组重复”元数据，该元数据由
 * `BlockPatternGroupedMixin` 注入：一个 aisle 可以表示整个 [getGroupSize] 个连续
 * aisle（async 扩展舱为 6），整体作为一个单元重复，而不是单个切片。
 *
 * [BlockPattern.aisleRepetitions] 保持其上游语义（[min, max] 倍重复从组第一个
 * aisle 开始的步骤）；组内部的 aisle 作为该步骤的一部分连续匹配，永远不会被
 * 单独访问。不是组起点的 aisle 索引报告大小为 1。
 *
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

    /** 声明从 [aisleIndex] 开始的步骤重复 [groupSize] 个连续 aisle。 / Declares that the step starting at [aisleIndex] repeats [groupSize] consecutive aisles. */
    fun setGroup(aisleIndex: Int, groupSize: Int)

    /** 从 [aisleIndex] 开始的步骤匹配的连续 aisle 数量（单个为 1）。 / Number of consecutive aisles matched by the step starting at [aisleIndex] (1 for singles). */
    fun getGroupSize(aisleIndex: Int): Int
}
