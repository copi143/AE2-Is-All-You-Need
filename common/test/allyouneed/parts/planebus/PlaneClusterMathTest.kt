package allyouneed.parts.planebus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaneClusterMathTest {

    private fun member(x: Int, y: Int, z: Int = 0, isBus: Boolean) =
        PlaneClusterMember(packPos(x, y, z), isBus)

    @Test
    fun `adjacent or equal covers six neighbourhood and self`() {
        val a = packPos(0, 0, 0)
        assertTrue(adjacentOrEqual(a, a))
        assertTrue(adjacentOrEqual(a, packPos(1, 0, 0)))
        assertTrue(adjacentOrEqual(a, packPos(-1, 0, 0)))
        assertTrue(adjacentOrEqual(a, packPos(0, 1, 0)))
        assertTrue(adjacentOrEqual(a, packPos(0, -1, 0)))
        assertTrue(adjacentOrEqual(a, packPos(0, 0, 1)))
        assertFalse(adjacentOrEqual(a, packPos(2, 0, 0)))
        assertFalse(adjacentOrEqual(a, packPos(0, 64, 0)))
    }

    @Test
    fun `packed positions round trip negative coordinates`() {
        val p = packPos(-1234567, -2000, 987654)
        assertEquals(-1234567, unpackPosX(p))
        assertEquals(-2000, unpackPosY(p))
        assertEquals(987654, unpackPosZ(p))
    }

    @Test
    fun `bus plus same-host plane forms a formed cluster`() {
        // 面板与专用线缆同一宿主方块（打包坐标相同）→ 入簇。
        // A plane sharing the bus's host block (same packed pos) joins the cluster.
        val result = PlaneClusterMath.compute(
            listOf(
                member(0, 0, isBus = true),
                PlaneClusterMember(packPos(0, 0, 0), isBus = false),
            ),
        )
        assertEquals(listOf(0, 0), result.clusterOfMember.toList())
        assertTrue(result.clusterFormed[0])
    }

    @Test
    fun `plane on an adjacent host stays out of the cluster`() {
        // AE2 中面板必须装在某个线缆宿主上：与专用线缆相邻的宿主 (1,0) 是另一条普通线缆，
        // 其面板属于那个网络，不得吸入本集群。
        // In AE2 a plane always lives on a cable host: the adjacent host (1,0) is another
        // ordinary cable whose plane belongs to that network and must not be absorbed.
        val result = PlaneClusterMath.compute(
            listOf(member(0, 0, isBus = true), member(1, 0, isBus = false)),
        )
        assertEquals(listOf(0, -1), result.clusterOfMember.toList())
        assertTrue(result.clusterFormed[0])
    }

    @Test
    fun `lone plane belongs to no cluster`() {
        val result = PlaneClusterMath.compute(
            listOf(member(5, 5, isBus = false), member(6, 5, isBus = false)),
        )
        // 面板之间不桥接：两者都保持未入簇。Planes never bridge; both stay unclustered.
        assertEquals(listOf(-1, -1), result.clusterOfMember.toList())
        assertTrue(result.clusterFormed.isEmpty())
    }

    @Test
    fun `plane joins only via its own host block`() {
        // 面板与总线同宿主 → 入簇；只接触其他面板或位于相邻宿主 → 不入簇。
        // Plane on the bus host → clustered. Planes touching only other planes or sitting on
        // an adjacent host → not clustered.
        val busPos = packPos(2, 0, 0)
        val result = PlaneClusterMath.compute(
            listOf(
                member(2, 0, isBus = true),
                PlaneClusterMember(busPos, isBus = false),
                member(3, 0, isBus = false),
                member(4, 0, isBus = false),
                member(2, 1, isBus = false),
            ),
        )
        assertEquals(0, result.clusterOfMember[0])
        assertEquals(0, result.clusterOfMember[1])
        assertEquals(-1, result.clusterOfMember[2])
        assertEquals(-1, result.clusterOfMember[3])
        assertEquals(-1, result.clusterOfMember[4])
    }

    @Test
    fun `buses chain into one cluster across distance`() {
        val members = mutableListOf<PlaneClusterMember>()
        // 线缆链：(0,0)→(10,0) 每格一个，链式相邻合并为一个集群。
        for (x in 0..10) members.add(member(x, 0, isBus = true))
        // 挂在 (5,0) 总线宿主上的面板（同一方块的其他面）。
        members.add(PlaneClusterMember(packPos(5, 0, 0), isBus = false))

        val result = PlaneClusterMath.compute(members)
        assertTrue(result.clusterOfMember.all { it == 0 })
        assertTrue(result.clusterFormed[0])
    }

    @Test
    fun `two disjoint runs stay separate clusters`() {
        val result = PlaneClusterMath.compute(
            listOf(
                member(0, 0, isBus = true),
                PlaneClusterMember(packPos(0, 0, 0), isBus = false),
                member(10, 0, isBus = true),
                PlaneClusterMember(packPos(10, 0, 0), isBus = false),
            ),
        )
        assertEquals(result.clusterOfMember[0], result.clusterOfMember[1])
        assertEquals(result.clusterOfMember[2], result.clusterOfMember[3])
        assertTrue(result.clusterOfMember[0] != result.clusterOfMember[2])
        assertTrue(result.clusterFormed.all { it })
    }

    @Test
    fun `exceeding bus cap leaves cluster unformed`() {
        val members = mutableListOf<PlaneClusterMember>()
        for (x in 0 until MAX_CLUSTER_BUSES + 1) {
            members.add(member(x, 0, isBus = true))
        }
        val result = PlaneClusterMath.compute(members)
        assertTrue(result.clusterOfMember.all { it == 0 })
        assertFalse(result.clusterFormed[0])
    }

    @Test
    fun `exceeding plane cap leaves cluster unformed`() {
        // 现实中一块线缆宿主可以在同一方块的其他面挂多块面板（打包坐标相同）。
        // 同位挂 MAX+1 块面板 → 超限不成型；MAX 块 → 成型。
        // In practice one bus host block can carry planes on its other faces (same packed pos).
        // MAX+1 same-pos planes exceed the cap; exactly MAX planes stay formed.
        val over = listOf(member(0, 0, isBus = true)) +
            List(MAX_CLUSTER_PLANES + 1) { PlaneClusterMember(packPos(0, 0, 0), isBus = false) }
        val overResult = PlaneClusterMath.compute(over)
        assertEquals(1, overResult.clusterOfMember.distinct().size)
        assertFalse(overResult.clusterFormed[0])

        val atLimit = listOf(member(0, 0, isBus = true)) +
            List(MAX_CLUSTER_PLANES) { PlaneClusterMember(packPos(0, 0, 0), isBus = false) }
        val okResult = PlaneClusterMath.compute(atLimit)
        assertTrue(okResult.clusterFormed[0])
    }

    @Test
    fun `blocked adjacency splits the chain into separate clusters`() {
        // 几何相邻但 shouldUnion 判 false（AE2 实际未连接，如被面板/石英纤维隔断）：
        // 两段各自成簇、各自按上限判成型。
        // Geometrically adjacent but shouldUnion says no (actually disconnected in AE2, e.g.
        // cut off by a plane/quartz fibre): both halves form their own cluster with their own
        // formed check.
        val members = listOf(member(0, 0, isBus = true), member(1, 0, isBus = true))
        val blocked = PlaneClusterMath.compute(members) { _, _ -> false }
        assertEquals(listOf(0, 1), blocked.clusterOfMember.toList())
        assertTrue(blocked.clusterFormed.all { it })

        // 同一输入、默认恒真过滤器 → 仍是一个集群（回归保护）。
        // Same input with the default always-true filter → still one cluster (regression guard).
        val connected = PlaneClusterMath.compute(members)
        assertEquals(listOf(0, 0), connected.clusterOfMember.toList())
        assertTrue(connected.clusterFormed[0])
    }

    @Test
    fun `blocked adjacency re-evaluates caps per segment`() {
        // 一条超上限的长链（MAX+1 条总线）在 x=16 处被实际隔断：拆成两段后各自不超过
        // 上限，因此都应判成型；若仍按合并计算则整体不成型。
        // A chain longer than the cap (MAX+1 buses) actually severed at x=16: both segments
        // fit the caps individually and must be formed; counting them merged would not be.
        val members = (0 until MAX_CLUSTER_BUSES + 1).map { member(it, 0, isBus = true) }
        // 切断 x=16 与 x=17 之间的无向边（真实连接判定天然对称）。
        // Cut the undirected edge between x=16 and x=17 (live connectivity checks are symmetric).
        val severed = PlaneClusterMath.compute(members) { a, b -> (a != 16 || b != 17) && (a != 17 || b != 16) }
        assertEquals(2, severed.clusterOfMember.distinct().size)
        assertTrue(severed.clusterFormed.all { it })
    }

    @Test
    fun `same block different faces merge into one cluster entry`() {
        // 同一方块上的两个不同面（打包坐标相同）视为一体。
        val same = packPos(7, 7, 7)
        val result = PlaneClusterMath.compute(
            listOf(
                PlaneClusterMember(same, isBus = true),
                PlaneClusterMember(same, isBus = true),
                PlaneClusterMember(same, isBus = false),
            ),
        )
        assertTrue(result.clusterOfMember.all { it == 0 })
        assertTrue(result.clusterFormed[0])
    }

    @Test
    fun `empty input yields empty result`() {
        val result = PlaneClusterMath.compute(emptyList())
        assertEquals(0, result.clusterOfMember.size)
        assertEquals(0, result.clusterFormed.size)
    }
}
