package allyouneed.parts.planebus

import appeng.api.networking.GridFlags
import appeng.api.networking.IGridMultiblock
import appeng.api.networking.IGridNode
import appeng.api.networking.IGridNodeListener
import appeng.api.parts.IPartCollisionHelper
import appeng.api.util.AECableType
import appeng.api.util.AEColor
import appeng.blockentity.networking.CableBusBlockEntity
import appeng.items.parts.ColoredPartItem
import appeng.parts.automation.AnnihilationPlanePart
import appeng.parts.automation.FormationPlanePart
import appeng.parts.networking.CablePart
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import java.util.Collections
import java.util.function.Predicate

/**
 * ME 破坏/成型面板专用线缆：智能线缆形态的真线缆部件（占用方块中心槽位），把相邻的
 * 破坏面板与成型面板组织成一个多方块结构（线缆之间链式互联，整个结构只消耗 1 个频道）。
 * 结构规则见 [PlaneClusterMath]。
 *
 * 频道分配完全走 AE2 原版多方块机制：本部件声明 [GridFlags.MULTIBLOCK] 并注册
 * [IGridMultiblock]，[getMultiblockNodes] 在集群成型时返回同集群的全部成员（总线与面板）。
 * BFS 寻路中首个拿到频道的成员会让其余成员零成本搭车（multiblocksWithChannel），因此
 * 共享祖先的频道瓶颈不会被集群内部占用打满；未成型或孤立时返回空集合，各成员独立付费，
 * 再由 [allyouneed.mixin.ae2.PathingCalculationMixin] 的未成型对账剥夺兜底——整个结构
 * 无法接入 ME 网络。
 *
 * 颜色固定为 [AEColor.TRANSPARENT]（透明可与任意颜色的线缆互联），且拒绝染色器换色，
 * 否则 [CablePart.changeColor] 会用原版智能线缆替换掉本部件、破坏集群语义。
 *
 * ME annihilation/forming plane bus: a true cable part in the smart-cable form (occupies the
 * centre slot of its host) that organises adjacent annihilation AND forming planes into a
 * multiblock structure (buses chain together) costing exactly one channel overall; see
 * [PlaneClusterMath] for the formation rules. Channel assignment uses the vanilla multiblock
 * mechanism: this part declares [GridFlags.MULTIBLOCK] and registers an [IGridMultiblock];
 * [getMultiblockNodes] returns every member of its cluster (buses and planes) when formed.
 * During BFS pathing the first member to receive a channel lets all others ride along for free
 * (multiblocksWithChannel), so the shared ancestor bottlenecks are never consumed by cluster
 * internals. When unformed (or isolated) it returns an empty iterator, members pay individually,
 * and the unformed reconciliation in [allyouneed.mixin.ae2.PathingCalculationMixin] strips them
 * so the whole structure stays disconnected from the ME network.
 *
 * The colour is pinned to [AEColor.TRANSPARENT] (transparent connects with cables of any
 * colour) and recolouring is refused: [CablePart.changeColor] would otherwise swap this part
 * for a vanilla smart cable and break the cluster semantics.
 */
class PlaneBusPart(partItem: ColoredPartItem<PlaneBusPart>) : CablePart(partItem), IGridMultiblock {

    init {
        mainNode.setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.PREFERRED, GridFlags.MULTIBLOCK)
        mainNode.addService(IGridMultiblock::class.java, this)
        mainNode.setIdlePowerUsage(1.0)
    }

    override fun addToWorld() {
        super.addToWorld()
        PlaneBusClusters.busAdded(blockEntity, side)
    }

    override fun removeFromWorld() {
        PlaneBusClusters.busRemoved(blockEntity, side)
        super.removeFromWorld()
    }

    /**
     * 集群成型时返回同集群全部成员的网格节点（总线与面板），供原版寻路按“整个结构一个
     * 频道”处理；未成型、孤立或客户端侧一律为空。成员键来自注册表，但节点解析会核对部件
     * 类型，避免注册表与方块实体短暂不同步时错误豁免无关部件。
     *
     * Returns the grid nodes of every member of this cluster (buses and planes) when formed, so
     * vanilla pathing treats the whole structure as one channel; empty when unformed, isolated or
     * on the client. Member keys come from the registry, but node resolution double-checks the
     * part type in case the registry and block entities are briefly out of sync.
     */
    override fun getMultiblockNodes(): Iterator<IGridNode> {
        val be = blockEntity ?: return Collections.emptyIterator()
        val level = be.level ?: return Collections.emptyIterator()
        if (level.isClientSide) return Collections.emptyIterator()

        val snapshot = PlaneBusClusters.snapshotFor(level.dimension())
        val clusterId = snapshot.clusterIdAtPos[be.blockPos] ?: return Collections.emptyIterator()
        if (!snapshot.formedById.getOrDefault(clusterId, false)) return Collections.emptyIterator()

        return snapshot.membersOf(clusterId).asSequence().mapNotNull { key ->
            val host = level.getBlockEntity(key.pos) as? CableBusBlockEntity ?: return@mapNotNull null
            when (val part = host.getPart(key.side)) {
                is PlaneBusPart, is AnnihilationPlanePart, is FormationPlanePart -> part.gridNode
                else -> null
            }
        }.iterator()
    }

    override fun getCableConnectionType(): AECableType = AECableType.SMART

    /** 智能线缆的频道指示点在状态变化时需要同步到客户端。Smart dots need sync on state changes. */
    override fun onMainNodeStateChanged(reason: IGridNodeListener.State) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            host.markForUpdate()
        }
    }

    override fun getBoxes(bch: IPartCollisionHelper, filterConnections: Predicate<Direction?>) {
        updateConnections()
        addNonDenseBoxes(bch, filterConnections, 5.0, 11.0)
    }

    /** 拒绝染色器：换色会替换成原版线缆。Refuse colour change: it would swap in a vanilla cable. */
    override fun changeColor(newColor: AEColor, who: Player): Boolean = false
}
