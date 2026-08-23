package allyouneed.parts.planebus

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

/** 面板子类型：破坏面板或成型面板。Plane subtype: annihilation or forming. */
enum class PlaneKind { ANNIHILATION, FORMATION }

/**
 * 破坏/成型面板专用线缆的成员注册表与集群快照缓存。
 *
 * - 部件在 addToWorld/removeFromWorld 时登记/注销自己（仅服务端）。
 * - 成员集合只在变化时标记脏；查询时懒重建集群划分。
 * - 集群成型状态是纯物理属性（位置邻接 + 数量上限），与 ME 网格无关；
 *   “是否同一网格”由调用方（寻路时遍历本网格节点）天然保证。
 *
 * Membership registry and cached cluster snapshots for plane buses.
 * Members register on addToWorld and unregister on removeFromWorld (server side only).
 * The member sets are dirty-flagged; cluster partitioning is rebuilt lazily on query.
 * Formed-ness is purely physical (position adjacency + size caps) and grid-independent;
 * the same-grid constraint is inherent because pathing reconciles one grid at a time.
 */
object PlaneBusClusters {

    /** 面板挂在各面上，线缆占用中心槽位，用 (pos, side?) 区分。Planes sit on faces, the bus occupies the centre slot; key by (pos, side?). */
    data class Key(val pos: BlockPos, val side: Direction?)

    private class LevelState {
        val buses = HashSet<Key>()
        val planes = HashMap<Key, PlaneKind>()
        var dirty = true

        var snapshot = Snapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }

    /** 集群不可变快照。Immutable cluster snapshot for one dimension. */
    class Snapshot(
        /** pos → 所属集群 id。pos → id of its cluster; positions outside any cluster are absent. */
        val clusterIdAtPos: Map<BlockPos, Int>,
        /** 集群 id → 是否成型。cluster id → formed flag. */
        val formedById: Map<Int, Boolean>,
        /** 集群 id → 已连接的破坏面板数。cluster id → connected annihilation plane count. */
        val annihilationsById: Map<Int, Int>,
        /** 集群 id → 已连接的成型面板数。cluster id → connected forming plane count. */
        val formingsById: Map<Int, Int>,
        /** 集群 id → 互联专用线缆数。cluster id → interconnected bus count. */
        val busesById: Map<Int, Int>,
        /** 集群 id → 全部成员键（总线与面板）。cluster id → all member keys (buses and planes). */
        private val membersById: Map<Int, List<Key>>,
    ) {
        /**
         * 查询某坐标所在集群的展示统计；不在任何集群时返回 null。
         *
         * Display stats of the cluster at the given position; null when not clustered.
         */
        fun infoAt(pos: BlockPos): Info? {
            val id = clusterIdAtPos[pos] ?: return null
            return Info(
                formedById.getOrDefault(id, false),
                annihilationsById.getOrDefault(id, 0),
                formingsById.getOrDefault(id, 0),
                busesById.getOrDefault(id, 0),
            )
        }

        /**
         * 某集群的全部成员键（总线与面板）；未知集群时返回空列表。
         *
         * All member keys of a cluster (buses and planes); empty for unknown clusters.
         */
        fun membersOf(clusterId: Int): List<Key> = membersById[clusterId].orEmpty()
    }

    /** 单个集群的展示统计（Jade 等信息显示用）。Per-cluster display stats (for Jade etc.). */
    data class Info(
        val formed: Boolean,
        val annihilations: Int,
        val formings: Int,
        val buses: Int,
    )

    private val levels = HashMap<ResourceKey<Level>, LevelState>()

    private fun state(level: ResourceKey<Level>): LevelState =
        levels.getOrPut(level) { LevelState() }

    fun addBus(level: ResourceKey<Level>, pos: BlockPos, side: Direction?) {
        state(level).let { it.buses.add(Key(pos.immutable(), side)); it.dirty = true }
    }

    fun removeBus(level: ResourceKey<Level>, pos: BlockPos, side: Direction?) {
        state(level).let { it.buses.remove(Key(pos, side)); it.dirty = true }
    }

    fun addPlane(level: ResourceKey<Level>, pos: BlockPos, side: Direction?, kind: PlaneKind) {
        state(level).let { it.planes[Key(pos.immutable(), side)] = kind; it.dirty = true }
    }

    fun removePlane(level: ResourceKey<Level>, pos: BlockPos, side: Direction?) {
        state(level).let { it.planes.remove(Key(pos, side)); it.dirty = true }
    }

    /**
     * 获取某维度的当前集群快照（脏时懒重建）。
     *
     * Current cluster snapshot of the given dimension, rebuilt lazily when dirty.
     */
    @JvmStatic
    fun snapshotFor(level: ResourceKey<Level>): Snapshot {
        val st = state(level)
        if (st.dirty) {
            st.snapshot = rebuild(st)
            st.dirty = false
        }
        return st.snapshot
    }

    private fun rebuild(st: LevelState): Snapshot {
        val memberCount = st.buses.size + st.planes.size
        val members = ArrayList<PlaneClusterMember>(memberCount)
        // 与成员下标对应，便于回填结果。Parallel to member indices for result mapping.
        val keys = ArrayList<Key>(memberCount)
        for (key in st.buses) {
            members.add(PlaneClusterMember(key.pos.asLong(), isBus = true))
            keys.add(key)
        }
        for ((key, kind) in st.planes) {
            members.add(PlaneClusterMember(key.pos.asLong(), isBus = false, isForming = kind == PlaneKind.FORMATION))
            keys.add(key)
        }

        val result = PlaneClusterMath.compute(members)

        val idAtPos = HashMap<BlockPos, Int>()
        val formedById = HashMap<Int, Boolean>()
        val annihilations = HashMap<Int, Int>()
        val formings = HashMap<Int, Int>()
        val buses = HashMap<Int, Int>()
        val membersById = HashMap<Int, MutableList<Key>>()
        for (i in keys.indices) {
            val cluster = result.clusterOfMember[i]
            if (cluster < 0) continue
            idAtPos[keys[i].pos] = cluster
            formedById[cluster] = result.clusterFormed[cluster]
            membersById.getOrPut(cluster) { ArrayList() }.add(keys[i])
            val member = members[i]
            if (member.isBus) buses.merge(cluster, 1, Int::plus)
            else if (member.isForming) formings.merge(cluster, 1, Int::plus)
            else annihilations.merge(cluster, 1, Int::plus)
        }
        return Snapshot(idAtPos, formedById, annihilations, formings, buses, membersById)
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
}
