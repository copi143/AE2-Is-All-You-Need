package allyouneed.multiblock

import allyouneed.multiblock.async.AsyncBlockKind
import net.minecraft.core.Direction

/**
 * 三种 async 合成结构的形状真相，手写、无数据驱动。
 *
 * 每种结构都位于自己的局部坐标系中，并锚定在控制器方块上：
 *
 *  - MODULE：3 宽（x）x 7 高（y）x 5 深（z）。工厂在正面 (1, 3, 0)。
 *  - SWITCH：19 宽 x 7 高 x (11 + 6N) 深。交换机方块在核心正面 (9, 4, 3)。
 *            底座核心 13x5x5；核心后方的地板承载扩展舱。
 *  - PROCESSOR：19 宽 x 15 高 x (19 + 6N) 深。控制器在核心正面 (9, 8, 3)。
 *            底座核心 13x13x13。
 *
 * 每个格子要么是必需的，要么是“无关”。必需格子必须包含 [blockAt] 返回的方块；
 * 当 [blockAt] 返回 null 时该格必须是空气（例如处理器 7x7 的空气层）。
 * 无关格子（[isDontCare]）接受任意方块。坐标方向：x = 西->东、y = 下->上、
 * z = 前->后。控制器面朝前方（局部 z 增加方向）；结构主体在控制器“背后”延伸。
 *
 * Hand-written, data-driven-free definitions of the three async synthesis structures.
 *
 * Each structure lives in a local coordinate system and is anchored at its controller block:
 *
 *  - MODULE:  3 wide (x) x 7 high (y) x 5 deep (z). Factory at (1, 3, 0) on the front face.
 *  - SWITCH:  19 wide x 7 high x (11 + 6N) deep. Switch block at (9, 4, 3) on the core's front
 *             face. Base core is 13x5x5; the floor behind the core carries the extension bays.
 *  - PROCESSOR: 19 wide x 15 high x (19 + 6N) deep. Controller at (9, 8, 3) on the core's front
 *             face. Base core is 13x13x13.
 *
 * A cell is either required or "don't care". A required cell must contain the block returned by
 * [blockAt]; when [blockAt] returns null the cell must be air (e.g. the processor's 7x7 air
 * layer). Don't-care cells ([isDontCare]) accept anything. Coordinates grow x = west->east,
 * y = bottom->top, z = front->back. The controller faces the front (increasing local z); the
 * structure body extends "behind" the controller.
 */
enum class AsyncStructureType(val baseDepth: Int) {
    MODULE(5), SWITCH(11), PROCESSOR(19),
}

object AsyncStructures {

    const val MAX_EXTENSIONS = 16
    const val EXTENSION_DEPTH = 6

    fun width(type: AsyncStructureType): Int = when (type) {
        AsyncStructureType.MODULE -> 3
        AsyncStructureType.SWITCH -> 19
        AsyncStructureType.PROCESSOR -> 19
    }

    fun height(type: AsyncStructureType): Int = when (type) {
        AsyncStructureType.MODULE -> 7
        AsyncStructureType.SWITCH -> 7
        AsyncStructureType.PROCESSOR -> 15
    }

    fun depth(type: AsyncStructureType, extensions: Int): Int = when (type) {
        AsyncStructureType.MODULE -> 5
        else -> type.baseDepth + EXTENSION_DEPTH * extensions
    }

    /** 局部坐标系中的锚点（控制器）格子。 / Anchor (controller) cell in local coordinates. */
    fun anchorCell(type: AsyncStructureType): Triple<Int, Int, Int> = when (type) {
        AsyncStructureType.MODULE -> Triple(1, 3, 0)
        AsyncStructureType.SWITCH -> Triple(9, 4, 3)
        AsyncStructureType.PROCESSOR -> Triple(9, 8, 3)
    }

    /**
     * 局部格子相对锚点、对水平朝向而言的世界偏移。局部 +y 朝上，局部 +z 沿
     * [facing] 方向，局部 +x 沿 facing 的顺时针一侧。
     *
     * World offset of a local cell relative to the anchor for a horizontal facing. Local +y is up,
     * local +z points along [facing] and local +x along the facing's clockwise side.
     */
    fun worldOffset(type: AsyncStructureType, facing: Direction, x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
        val (ax, ay, az) = anchorCell(type)
        val right = facing.clockWise
        return Triple(
            (x - ax) * right.stepX + (z - az) * facing.stepX,
            y - ay,
            (x - ax) * right.stepZ + (z - az) * facing.stepZ,
        )
    }

    /**
     * 一个局部格子是否可以放任意方块。所有其他在界格子都是必需的；必需方块与
     * 必需空气的区别见类文档。
     *
     * Whether a local cell may contain anything. All other in-bounds cells are required; see the
     * class comment for the distinction between required blocks and required air.
     */
    fun isDontCare(type: AsyncStructureType, x: Int, y: Int, z: Int): Boolean {
        if (x < 0 || y < 0 || z < 0) return false
        if (x >= width(type) || y >= height(type)) return false
        return when (type) {
            AsyncStructureType.MODULE -> false
            AsyncStructureType.SWITCH -> y in 2..<height(type) && !inCore(type, x, y, z)
            AsyncStructureType.PROCESSOR -> y in 2..<height(type) && !inCore(type, x, y, z)
        }
    }

    /**
     * 局部格子处期望的方块种类。只对必需格子有意义：必需空气格返回 null。
     * 无关格子请忽略该返回值。
     *
     * Expected block kind at a local cell. Only meaningful for required cells: returns null for
     * required-air cells. Ignore it for don't-care cells.
     */
    fun blockAt(type: AsyncStructureType, extensions: Int, x: Int, y: Int, z: Int): AsyncBlockKind? {
        if (x < 0 || y < 0 || z < 0) return null
        if (x >= width(type) || y >= height(type) || z >= depth(type, extensions)) return null
        return when (type) {
            AsyncStructureType.MODULE -> moduleAt(x, y, z)
            AsyncStructureType.SWITCH -> switchAt(extensions, x, y, z)
            AsyncStructureType.PROCESSOR -> processorAt(extensions, x, y, z)
        }
    }

    /**
     * 一个 [actual] 种类的方块在局部格子处是否可接受。处理替换规则：机器玻璃可以
     * 替换墙上的机器方块，核心机器方块可以被匹配的连接器替换。
     *
     * Whether a block of [actual] kind is acceptable at a local cell. Handles the replacement rules:
     * machine glass may replace machine blocks on walls, and core machine blocks may be replaced by
     * the matching connectors.
     */
    fun isValidCell(
        type: AsyncStructureType,
        extensions: Int,
        x: Int,
        y: Int,
        z: Int,
        actual: AsyncBlockKind
    ): Boolean {
        val expected = blockAt(type, extensions, x, y, z) ?: return true
        if (expected == actual) return true

        if (expected == AsyncBlockKind.MACHINE && actual == AsyncBlockKind.GLASS && !isFloorCell(type, x, y, z)) {
            return true
        }

        return when (type) {
            AsyncStructureType.MODULE -> false
            AsyncStructureType.SWITCH -> expected == AsyncBlockKind.MACHINE && inCore(type, x, y, z) &&
                    (actual == AsyncBlockKind.WAN_CONNECTOR || actual == AsyncBlockKind.LAN_CONNECTOR)

            AsyncStructureType.PROCESSOR -> expected == AsyncBlockKind.MACHINE && inCore(type, x, y, z) &&
                    isOuterShellCell(x, y, z) &&
                    (actual == AsyncBlockKind.ME_CONNECTOR || actual == AsyncBlockKind.LAN_CONNECTOR)
        }
    }

    /** [y] 是否属于地板的一部分（只有地板以上的墙才允许玻璃替换）。 / Whether [y] is part of the floor (walls may be glass-replaced only above the floor). */
    fun isFloorCell(type: AsyncStructureType, x: Int, y: Int, z: Int): Boolean = when (type) {
        AsyncStructureType.MODULE -> y == 0 || y == height(type) - 1
        else -> y == 0 || y == 1
    }

    /** 局部格子是否位于交换机/处理器核心内（连接器可以在这里替换）。 / Whether a local cell lies inside the switch/processor core (connectors may replace there). */
    fun inCore(type: AsyncStructureType, x: Int, y: Int, z: Int): Boolean {
        val b = coreBounds(type)
        return x in b[0]..b[1] && y in b[2]..b[3] && z in b[4]..b[5]
    }

    private fun coreBounds(type: AsyncStructureType): IntArray = when (type) {
        AsyncStructureType.SWITCH -> intArrayOf(3, 15, 2, 6, 3, 7)
        AsyncStructureType.PROCESSOR -> intArrayOf(3, 15, 2, 14, 3, 15)
        else -> throw IllegalStateException("no core")
    }

    /** 处理器核心格子是否恰好位于一个壳面之上（ME/LAN 可以在这里替换）。 / Whether a processor core cell lies on exactly one shell face (ME/LAN may replace there). */
    fun isOuterShellCell(x: Int, y: Int, z: Int): Boolean {
        val b = coreBounds(AsyncStructureType.PROCESSOR)
        var count = 0
        if (x == b[0] || x == b[1]) count++
        if (y == b[2] || y == b[3]) count++
        if (z == b[4] || z == b[5]) count++
        return count == 1
    }

    // ---------------------------------------------------------------------------------------------
    // MODULE (3 x 7 x 5)
    // ---------------------------------------------------------------------------------------------

    private fun moduleAt(x: Int, y: Int, z: Int): AsyncBlockKind? {
        if (y == 0 || y == height(AsyncStructureType.MODULE) - 1) {
            return when {
                x == 0 || x == 2 -> AsyncBlockKind.FRAME
                z == 0 || z == 4 -> AsyncBlockKind.FRAME
                else -> AsyncBlockKind.MACHINE
            }
        }
        if (z == 0) {
            return when {
                x == 0 || x == 2 -> AsyncBlockKind.FRAME
                y == 3 -> AsyncBlockKind.FACTORY
                else -> AsyncBlockKind.MACHINE
            }
        }
        if (z == 4) {
            return if (x == 0 || x == 2) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
        }
        if (x == 0 || x == 2) {
            return AsyncBlockKind.MACHINE
        }
        return when (z) {
            1, 3 -> AsyncBlockKind.TOWER
            2 -> if (y == 3) AsyncBlockKind.EXECUTION else AsyncBlockKind.ENERGY
            else -> null
        }
    }

    /** 模块底部中心正下方的模块接口（Z）格子。 / The module interface (Z) cell directly below the module's bottom centre. */
    val moduleInterfaceCell: Triple<Int, Int, Int> = Triple(1, -1, 2)

    /** 工厂锚点到模块接口的世界偏移。 / World offset from the factory anchor to the module interface. */
    fun interfaceWorldOffset(facing: Direction): Triple<Int, Int, Int> {
        val (x, y, z) = moduleInterfaceCell
        return worldOffset(AsyncStructureType.MODULE, facing, x, y, z)
    }

    // ---------------------------------------------------------------------------------------------
    // SWITCH (19 x 7 x (11 + 6N))
    // ---------------------------------------------------------------------------------------------

    private fun switchAt(extensions: Int, x: Int, y: Int, z: Int): AsyncBlockKind? {
        val d = depth(AsyncStructureType.SWITCH, extensions)
        if (y == 0) {
            return if (x == 0 || x == 18 || z == 0 || z == d - 1) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
        }
        if (y == 1) {
            return upperFloorCell(AsyncStructureType.SWITCH, extensions, x, z)
        }
        if (x in 3..15 && z in 3..7) {
            return switchCoreCell(x, y, z)
        }
        return null
    }

    private fun switchCoreCell(x: Int, y: Int, z: Int): AsyncBlockKind? {
        val edgeX = x == 3 || x == 15
        val edgeY = y == 2 || y == 6
        val edgeZ = z == 3 || z == 7
        var edges = 0
        if (edgeX) edges++
        if (edgeY) edges++
        if (edgeZ) edges++
        if (edges >= 2) return AsyncBlockKind.FRAME
        if (edges == 1) {
            return if (x == 9 && y == 4 && z == 3) AsyncBlockKind.SWITCH else AsyncBlockKind.MACHINE
        }
        return when (z) {
            4, 6 -> if (y == 4) AsyncBlockKind.ENERGY else AsyncBlockKind.TOWER
            5 -> if (y == 4) AsyncBlockKind.COMPUTING else AsyncBlockKind.ENERGY
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PROCESSOR (19 x 15 x (19 + 6N))
    // ---------------------------------------------------------------------------------------------

    private fun processorAt(extensions: Int, x: Int, y: Int, z: Int): AsyncBlockKind? {
        val d = depth(AsyncStructureType.PROCESSOR, extensions)
        if (y == 0) {
            return if (x == 0 || x == 18 || z == 0 || z == d - 1) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
        }
        if (y == 1) {
            return upperFloorCell(AsyncStructureType.PROCESSOR, extensions, x, z)
        }
        if (x in 3..15 && y in 2..14 && z in 3..15) {
            return processorCoreCell(x, y, z)
        }
        return null
    }

    private fun processorCoreCell(x: Int, y: Int, z: Int): AsyncBlockKind? {
        val lx = minOf(x - 3, 15 - x)
        val ly = minOf(y - 2, 14 - y)
        val lz = minOf(z - 3, 15 - z)
        val d = minOf(lx, ly, lz)

        val atLayer = fun(v: Int, layer: Int): Boolean = v == layer

        return when (d) {
            0 -> {
                var count = 0
                if (atLayer(lx, 0)) count++
                if (atLayer(ly, 0)) count++
                if (atLayer(lz, 0)) count++
                if (count >= 2) {
                    AsyncBlockKind.FRAME
                } else {
                    if (x == 9 && y == 8 && z == 3) AsyncBlockKind.CONTROLLER else AsyncBlockKind.MACHINE
                }
            }

            1 -> {
                var count = 0
                if (atLayer(lx, 1)) count++
                if (atLayer(ly, 1)) count++
                if (atLayer(lz, 1)) count++
                if (count == 3) {
                    AsyncBlockKind.FRAME
                } else if (count == 2) {
                    AsyncBlockKind.TOWER
                } else if (lx == 2 || ly == 2 || lz == 2) {
                    AsyncBlockKind.ENERGY
                } else {
                    null
                }
            }

            2 -> {
                var count = 0
                if (atLayer(lx, 2)) count++
                if (atLayer(ly, 2)) count++
                if (atLayer(lz, 2)) count++
                when (count) {
                    3 -> AsyncBlockKind.FRAME
                    2 -> AsyncBlockKind.TOWER
                    else -> null
                }
            }

            3 -> null
            4 -> AsyncBlockKind.COMPUTING
            else -> AsyncBlockKind.STORAGE
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared floor / bay logic
    // ---------------------------------------------------------------------------------------------

    /**
     * y = 1 处的上层地板。地板是实心的：除框架列、模块接口（Z）和塔行外全部是
     * 机器方块；核心底面积被核心盖住，但按 README（“地板是实心的，标 X 的地方
     * 也是机器方块”）仍填机器方块。扩展舱追加在核心后方，每舱 6 格，从近到远
     * 依次为：塔、分隔行、舱内、Z 舱内、舱内、分隔行（与 README 从后向前绘制的
     * 图一致），在局部 x = 5 与 x = 13 处各提供一个模块接口（Z）。
     *
     * The upper floor at y = 1. The floor is solid: machine everywhere except the frame columns,
     * the module interfaces (Z) and the tower rows. The core footprint is covered by the core but is
     * still machine per the README ("the floor is solid; the X cells are machine blocks too").
     * Extension bays are appended behind the core, each six cells deep, near to far: tower, split
     * row, bay interior, Z interior, bay interior, split row (matching the README's back-to-front
     * drawing), providing two module interfaces (Z) at local x = 5 and x = 13.
     */
    private fun upperFloorCell(type: AsyncStructureType, extensions: Int, x: Int, z: Int): AsyncBlockKind? {
        val d = depth(type, extensions)
        val upperZ1 = 1
        val upperZ2 = d - 2
        if (x !in 1..17 || z < upperZ1 || z > upperZ2) return null
        if (x == 1 || x == 17 || z == upperZ1 || z == upperZ2) return AsyncBlockKind.FRAME

        val bayStart = if (type == AsyncStructureType.SWITCH) 9 else 17
        if ((z - bayStart) / EXTENSION_DEPTH !in 0 until extensions) return AsyncBlockKind.MACHINE

        return when ((z - bayStart) % EXTENSION_DEPTH) {
            0 -> if (x == 1 || x == 17) AsyncBlockKind.FRAME else AsyncBlockKind.TOWER
            3 -> when (x) {
                5, 13 -> AsyncBlockKind.MODULE_INTERFACE
                1, 9, 17 -> AsyncBlockKind.FRAME
                else -> AsyncBlockKind.MACHINE
            }

            else -> if (x == 1 || x == 9 || x == 17) AsyncBlockKind.FRAME else AsyncBlockKind.MACHINE
        }
    }
}
