package allyouneed.parts.planebus

/**
 * 破坏/成型面板专用线缆的集群规模上限：单结构最多 32 个线缆部件、64 个面板
 * （破坏与成型合计）。超出任一上限则整个结构不成型，所有成员（线缆与面板）
 * 都无法从 ME 网络获得频道。
 *
 * Capacity caps for a plane bus cluster: at most 32 bus parts and 64 planes (annihilation
 * and forming combined) per structure. Exceeding either cap leaves the whole structure
 * unformed, denying channels to every member (buses and planes alike).
 */
const val MAX_CLUSTER_BUSES = 32
const val MAX_CLUSTER_PLANES = 64

/**
 * 集群成员的纯位置描述，与 Minecraft 类型解耦以便单元测试。
 *
 * Position-only description of a cluster member, decoupled from Minecraft types for testing.
 */
data class PlaneClusterMember(
    /** 打包的世界坐标。Packed world position. */
    val pos: Long,
    /** true 为专用线缆部件，false 为破坏/成型面板。true for a bus part, false for a plane. */
    val isBus: Boolean,
    /** 面板子类型（仅 isBus=false 时有意义）。Plane subtype (only meaningful when !isBus). */
    val isForming: Boolean = false,
)

/** 打包坐标（同 [net.minecraft.core.BlockPos.asLong] 的位布局）。Packed position, same layout as [net.minecraft.core.BlockPos.asLong]. */
fun packPos(x: Int, y: Int, z: Int): Long =
    ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

fun unpackPosX(p: Long): Int = (p shr 38).toInt()

fun unpackPosY(p: Long): Int = ((p and 0xFFFL).toInt() shl 20) shr 20

fun unpackPosZ(p: Long): Int = ((p shl 26) shr 38).toInt()

/** 相同方块或六向相邻返回 true。Equal block or directly adjacent (6-way). */
fun adjacentOrEqual(a: Long, b: Long): Boolean =
    a == b ||
        Math.abs(unpackPosX(a) - unpackPosX(b)) +
        Math.abs(unpackPosY(a) - unpackPosY(b)) +
        Math.abs(unpackPosZ(a) - unpackPosZ(b)) == 1

/**
 * 集群划分结果。
 *
 * - [clusterOfMember]：成员下标 → 集群下标；孤立面板（周围没有任何线缆）为 -1，
 *   表示不属于任何集群、保持原版行为。
 * - [clusterFormed]：集群下标 → 是否成型（满足线缆/面板数量限制）。
 *
 * Result of clustering:
 * - [clusterOfMember]: member index → cluster index; a lone plane with no neighbouring bus is
 *   -1, meaning "not part of any cluster" and therefore keeps vanilla behaviour.
 * - [clusterFormed]: cluster index → whether the size caps are satisfied.
 */
class PlaneClusterResult(
    val clusterOfMember: IntArray,
    val clusterFormed: BooleanArray,
)

object PlaneClusterMath {

    /**
     * 并查集分簇规则：
     *
     * - 线缆↔线缆：相同方块或六向相邻（链式互联）。
     * - 线缆↔面板：仅限同一宿主方块——AE2 面板必须装在某个线缆宿主上，与专用线缆
     *   相邻的宿主是别的线缆，其面板属于那个网络，不得吸入。
     * - 面板↔面板：永不连接。
     *
     * [shouldUnion] 是可选的额外边过滤器（参数为成员下标），返回 false 时即便满足上述
     * 几何规则也不合并——用于剔除“位置相邻但被面板/石英纤维等隔断、AE2 实际未连接”
     * 的假邻居。默认恒真，即纯几何行为。
     *
     * Groups members into clusters using union-find:
     *
     * - bus↔bus: equal or adjacent positions (chained buses).
     * - bus↔plane: same host block only — in AE2 a plane always lives on some cable host,
     *   so a host adjacent to the bus is a different cable whose planes belong elsewhere.
     * - plane↔plane: never.
     *
     * [shouldUnion] is an optional extra edge filter (member indices); returning false keeps
     * the pair apart even when the geometric rules hold — used to reject "adjacent but actually
     * disconnected by planes/quartz fibre etc." fake neighbours. Defaults to always true, i.e.
     * pure geometry.
     */
    fun compute(
        members: List<PlaneClusterMember>,
        shouldUnion: (a: Int, b: Int) -> Boolean = { _, _ -> true },
    ): PlaneClusterResult {
        val n = members.size
        val parent = IntArray(n) { it }

        fun find(i: Int): Int {
            var root = i
            while (parent[root] != root) root = parent[root]
            var cur = i
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun tryUnion(i: Int, j: Int) {
            val mi = members[i]
            val mj = members[j]
            // 面板之间不允许桥接。Planes must never bridge each other.
            if (!mi.isBus && !mj.isBus) return
            // 面板必须与专用线缆处于同一宿主方块（AE2 面板总装在某个线缆宿主上，
            // 相邻宿主上的面板属于别的普通线缆，不得吸入本集群）。
            // A plane only joins through its own host block: in AE2 every plane lives on some
            // cable host, so a plane on an *adjacent* host belongs to that other cable and must
            // not be absorbed into this cluster.
            if (mi.isBus != mj.isBus && mi.pos != mj.pos) return
            if (!shouldUnion(i, j)) return
            val ri = find(i)
            val rj = find(j)
            if (ri != rj) parent[ri] = rj
        }

        // 位置 → 同位置成员；相邻查询只需检查 7 个键（自身位 + 6 邻位）。
        // pos → members sharing that pos; neighbour queries touch only 7 keys (self + 6 sides).
        val byPos = HashMap<Long, MutableList<Int>>()
        members.forEachIndexed { i, m -> byPos.getOrPut(m.pos) { mutableListOf() }.add(i) }

        val offsets = longArrayOf(
            packPos(1, 0, 0), packPos(-1, 0, 0),
            packPos(0, 1, 0), packPos(0, -1, 0),
            packPos(0, 0, 1), packPos(0, 0, -1),
        )

        for ((pos, list) in byPos) {
            for (a in list) {
                for (b in list) {
                    if (a != b) tryUnion(a, b)
                }
            }
            for (off in offsets) {
                val neighbours = byPos[pos + off] ?: continue
                for (a in list) {
                    for (b in neighbours) {
                        if (a != b) tryUnion(a, b)
                    }
                }
            }
        }

        // 汇总每个根的统计；不含线缆的分组不是集群，其成员回到未入簇状态。
        // Aggregate per-root stats; groups without any bus are not clusters.
        val busCount = HashMap<Int, Int>()
        val planeCount = HashMap<Int, Int>()
        val rawCluster = IntArray(n) { -1 }
        for (i in 0 until n) {
            val root = find(i)
            rawCluster[i] = root
            if (members[i].isBus) busCount.merge(root, 1, Int::plus) else planeCount.merge(root, 1, Int::plus)
        }

        // 集群下标按首次出现顺序分配，成型判定每集群只算一次。
        // Cluster indices are assigned in first-appearance order; the formed check runs once per cluster.
        val remap = HashMap<Int, Int>()
        val memberOut = IntArray(n) { -1 }
        val formed = mutableListOf<Boolean>()
        for (i in 0 until n) {
            val root = rawCluster[i]
            if (busCount.getOrDefault(root, 0) == 0) continue // 孤立面板。Lone plane.
            memberOut[i] = remap.getOrPut(root) {
                formed.add(
                    busCount.getOrDefault(root, 0) in 1..MAX_CLUSTER_BUSES &&
                        planeCount.getOrDefault(root, 0) <= MAX_CLUSTER_PLANES,
                )
                formed.lastIndex
            }
        }

        return PlaneClusterResult(memberOut, formed.toBooleanArray())
    }
}
