package allyouneed.parts.planebus

import appeng.api.networking.IGridNode
import appeng.blockentity.networking.CableBusBlockEntity
import appeng.parts.automation.AnnihilationPlanePart
import appeng.parts.automation.FormationPlanePart
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

/** 面板子类型：破坏面板或成型面板。Plane subtype: annihilation or forming. */
enum class PlaneKind { ANNIHILATION, FORMATION }

/**
 * 破坏/成型面板专用线缆的成员注册表与集群解析。
 *
 * - 部件在 addToWorld/removeFromWorld 时登记/注销自己（仅服务端）。
 * - [resolve] 每次调用都基于当前世界状态现算集群：几何规则（[PlaneClusterMath]）之上，
 *   再对跨宿主的总线相邻边实时校验两个 ME 网格节点是否真的直连——位置相邻但被面板、
 *   石英纤维等隔断的假邻居不会并入同一集群。因此频道计算（走真实网格）、tooltip 统计与
 *   未成型剥夺三者看到的是同一个拓扑。
 * - 成员规模很小（≤32 总线 + ≤64 面板），现算的开销可忽略；不做缓存是为了避免“放置
 *   隔断物不触发注册表脏标记”导致的陈旧拓扑。
 *
 * Membership registry and cluster resolution for plane buses.
 *
 * - Parts register/unregister themselves in addToWorld/removeFromWorld (server side only).
 * - [resolve] recomputes clusters from the live world on every call: on top of the geometric
 *   rules ([PlaneClusterMath]) every cross-host bus adjacency is verified against the actual
 *   ME network — two grid nodes that merely touch positions but are cut off by planes, quartz
 *   fibre etc. never share a cluster. Channel pathing, tooltip stats and the unformed strip all
 *   therefore see the same topology.
 * - Member counts are tiny (≤32 buses + ≤64 planes) so recomputing is negligible; no caching,
 *   because placing a blocker does not dirty the registry and a cached topology could go stale.
 */
object PlaneBusClusters {

    /** 面板挂在各面上，线缆占用中心槽位，用 (pos, side?) 区分。Planes sit on faces, the bus occupies the centre slot; key by (pos, side?). */
    data class Key(val pos: BlockPos, val side: Direction?)

    private class LevelState {
        val buses = HashSet<Key>()
        val planes = HashMap<Key, PlaneKind>()
    }

    /** 单个集群的展示统计（tooltip 用）。Per-cluster display stats (for tooltips). */
    data class Info(
        val formed: Boolean,
        val annihilations: Int,
        val formings: Int,
        val buses: Int,
    )

    /**
     * 一次解析得到的集群视图：成员 → 集群 id、成型状态、成员列表与统计。
     * All keys of the resolved view: key → cluster id, formed flags, member lists and stats.
     */
    class PlaneClusters(
        private val idAtKey: Map<Key, Int>,
        val formedById: Map<Int, Boolean>,
        private val membersById: Map<Int, List<Key>>,
        private val infoById: Map<Int, Info>,
        /** pos → 集群 id（该 pos 任一成员所属）。pos → id of the cluster owning any member there. */
        private val idAtPos: Map<BlockPos, Int>,
    ) {
        /** 成员所在集群 id；未入簇返回 null。Cluster id of a member; null when unclustered. */
        fun idAt(pos: BlockPos, side: Direction?): Int? = idAtKey[Key(pos, side)]

        /** 某集群的全部成员键；未知集群时返回空列表。All member keys of a cluster; empty for unknown clusters. */
        fun membersOf(clusterId: Int): List<Key> = membersById[clusterId].orEmpty()

        /**
         * 某坐标所在集群的展示统计；不在任何集群时返回 null。
         * Display stats of the cluster at the given position; null when not clustered.
         */
        fun infoAt(pos: BlockPos): Info? {
            val id = idAtPos[pos] ?: return null
            return infoById[id]
        }
    }

    private val EMPTY = PlaneClusters(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

    private val levels = HashMap<ResourceKey<Level>, LevelState>()

    private fun state(level: ResourceKey<Level>): LevelState =
        levels.getOrPut(level) { LevelState() }

    fun addBus(level: ResourceKey<Level>, pos: BlockPos, side: Direction?) {
        state(level).buses.add(Key(pos.immutable(), side))
    }

    fun removeBus(level: ResourceKey<Level>, pos: BlockPos, side: Direction?) {
        state(level).buses.remove(Key(pos, side))
    }

    fun addPlane(level: ResourceKey<Level>, pos: BlockPos, side: Direction?, kind: PlaneKind) {
        state(level).planes[Key(pos.immutable(), side)] = kind
    }

    fun removePlane(level: ResourceKey<Level>, pos: BlockPos, side: Direction?) {
        state(level).planes.remove(Key(pos, side))
    }

    // ---- 部件钩子的便捷封装。Convenience wrappers for part hooks. ----

    @JvmStatic
    fun busAdded(be: BlockEntity, side: Direction?) {
        val level = be.level ?: return
        if (!level.isClientSide) addBus(level.dimension(), be.blockPos, side)
    }

    @JvmStatic
    fun busRemoved(be: BlockEntity, side: Direction?) {
        val level = be.level ?: return
        if (!level.isClientSide) removeBus(level.dimension(), be.blockPos, side)
    }

    @JvmStatic
    fun planeAdded(be: BlockEntity, side: Direction?, kind: PlaneKind) {
        val level = be.level ?: return
        if (!level.isClientSide) addPlane(level.dimension(), be.blockPos, side, kind)
    }

    @JvmStatic
    fun planeRemoved(be: BlockEntity, side: Direction?) {
        val level = be.level ?: return
        if (!level.isClientSide) removePlane(level.dimension(), be.blockPos, side)
    }

    // ---- 集群解析。Cluster resolution. ----

    /**
     * 解析某维度的当前集群。服务端专用；客户端或空注册表返回空视图。
     * Resolves the current clusters of a dimension; server-side only, an empty view on the
     * client or with an empty registry.
     */
    @JvmStatic
    fun resolve(level: Level?): PlaneClusters {
        if (level == null || level.isClientSide) {
            return EMPTY
        }
        val st = state(level.dimension())
        if (st.buses.isEmpty() && st.planes.isEmpty()) {
            return EMPTY
        }

        val keys = ArrayList<Key>(st.buses.size + st.planes.size)
        val members = ArrayList<PlaneClusterMember>(keys.size)
        for (key in st.buses) {
            keys.add(key)
            members.add(PlaneClusterMember(key.pos.asLong(), isBus = true))
        }
        for ((key, kind) in st.planes) {
            keys.add(key)
            members.add(PlaneClusterMember(key.pos.asLong(), isBus = false, isForming = kind == PlaneKind.FORMATION))
        }

        val result = PlaneClusterMath.compute(members) { i, j ->
            val ki = keys[i]
            val kj = keys[j]
            // 同一宿主方块内的部件必然同网格；只有跨方块的总线相邻边需要实测。
            // Parts sharing a host block are always in one grid; only cross-block bus edges
            // need a live check.
            ki.pos == kj.pos || areNodesConnected(level, ki, kj)
        }

        val idAtKey = HashMap<Key, Int>()
        val membersById = HashMap<Int, MutableList<Key>>()
        val idAtPos = HashMap<BlockPos, Int>()
        val busCounts = HashMap<Int, Int>()
        val formingCounts = HashMap<Int, Int>()
        val annihilationCounts = HashMap<Int, Int>()
        for (i in keys.indices) {
            val cluster = result.clusterOfMember[i]
            if (cluster < 0) continue
            val key = keys[i]
            idAtKey[key] = cluster
            idAtPos.putIfAbsent(key.pos, cluster)
            membersById.getOrPut(cluster) { ArrayList() }.add(key)
            val member = members[i]
            when {
                member.isBus -> busCounts.merge(cluster, 1, Int::plus)
                member.isForming -> formingCounts.merge(cluster, 1, Int::plus)
                else -> annihilationCounts.merge(cluster, 1, Int::plus)
            }
        }

        val formedById = HashMap<Int, Boolean>(membersById.size)
        val infoById = HashMap<Int, Info>(membersById.size)
        for ((cluster, memberKeys) in membersById) {
            val formed = result.clusterFormed[cluster]
            formedById[cluster] = formed
            infoById[cluster] = Info(
                formed,
                annihilationCounts.getOrDefault(cluster, 0),
                formingCounts.getOrDefault(cluster, 0),
                busCounts.getOrDefault(cluster, 0),
            )
        }
        return PlaneClusters(idAtKey, formedById, membersById, infoById, idAtPos)
    }

    /**
     * 解析成员键对应的网格节点（带部件类型核对，防注册表与方块实体短暂不同步）。
     * Resolves the grid node of a member key (double-checks the part type in case the registry
     * and block entities are briefly out of sync).
     */
    internal fun memberNode(level: Level, key: Key): IGridNode? {
        val host = level.getBlockEntity(key.pos) as? CableBusBlockEntity ?: return null
        return when (val part = host.getPart(key.side)) {
            is PlaneBusPart, is AnnihilationPlanePart, is FormationPlanePart -> part.gridNode
            else -> null
        }
    }

    private fun areNodesConnected(level: Level, a: Key, b: Key): Boolean {
        val na = memberNode(level, a) ?: return false
        val nb = memberNode(level, b) ?: return false
        return na.connections.any { it.getOtherSide(na) === nb }
    }
}
