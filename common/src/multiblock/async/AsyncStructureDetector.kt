package allyouneed.multiblock.async

import allyouneed.multiblock.AsyncStructureType
import allyouneed.multiblock.AsyncStructures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.properties.BlockStateProperties

/**
 * 三种 async 合成结构的“锚点检测器”。
 *
 * 形状真相（shape truth）在 [AsyncStructures] 中。检测从控制器锚点开始，用
 * 控制器的朝向推导结构取向，探测扩展舱数量（0..[AsyncStructures.MAX_EXTENSIONS]）
 * 并校验每个在界内的格子，区分三类：
 *
 *  - 必需方块（[AsyncStructures.blockAt] 返回某个种类，用 [AsyncStructures.isValidCell] 校验）
 *  - 必需空气（[AsyncStructures.blockAt] 返回 null，该格必须为空）
 *  - 无关格子（[AsyncStructures.isDontCare]），接受任意方块
 *
 * 模块从地板接口（Z）向上检测（3x7x5）；交换机扫描其扩展舱接口上的所有模块；
 * 处理器扫描自身的模块以及经 LAN/WAN 连接器和专用线缆可达的交换机（直接或级联）。
 *
 * Anchor-based detector for the three async synthesis structures.
 *
 * The shape truth lives in [AsyncStructures]. Detection starts at a controller anchor, derives the
 * structure orientation from the controller's facing, probes the extension count (0..[AsyncStructures.MAX_EXTENSIONS])
 * and validates every in-bounds cell, distinguishing:
 *
 *  - required blocks ([AsyncStructures.blockAt] returns a kind, validated via [AsyncStructures.isValidCell])
 *  - required air ([AsyncStructures.blockAt] returns null, the cell must be empty)
 *  - don't-care cells ([AsyncStructures.isDontCare]) which accept anything
 *
 * A module is detected from its floor interface (Z) upward (3x7x5); a switch scans the modules on
 * its extension-bay interfaces; the processor scans its own modules plus the switches reachable
 * through LAN/WAN connectors and dedicated cable (directly or cascaded).
 */
object AsyncStructureDetector {

    /**
     * GTCEu 控制器查找钩子。Forge 在 GTCEu 加载时设置；Fabric / 无 GT 时保持 null。
     * 不得在本文件引用 GTCEu 类型。
     *
     * Hook for locating GTCEu controllers. Set on Forge when GTCEu is loaded; stays null on
     * Fabric / without GT. This file must not reference GTCEu types.
     */
    @JvmField
    var extraFinder: ((ServerLevel, BlockPos) -> IAsyncStructureHost?)? = null

    fun kindOf(level: ServerLevel, pos: BlockPos): AsyncBlockKind? {
        val block = level.getBlockState(pos).block
        return (block as? IAsyncKindBlock)?.kind
    }

    fun facingOf(level: ServerLevel, pos: BlockPos): Direction? {
        val state = level.getBlockState(pos)
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Module (3 x 7 x 5), mounted on a floor interface (Z)
    // ---------------------------------------------------------------------------------------------

    /**
     * 检测 [interfacePos] 地板接口上搭建的模块。接口方块的朝向固定模块取向；
     * 工厂控制器位于接口正下后方，朝向必须一致。3x7x5 每一格都是必需方块。
     *
     * Detects the module built on the [interfacePos] floor interface. The interface block's facing
     * fixes the module orientation; the factory controller is found directly below/behind it and must
     * face the same way. Every one of the 3x7x5 cells is required.
     */
    fun detectModule(level: ServerLevel, interfacePos: BlockPos): AsyncModuleCluster? {
        if (kindOf(level, interfacePos) != AsyncBlockKind.MODULE_INTERFACE) return null
        if (level.getBlockEntity(interfacePos) !is AsyncStructureBlockEntity) return null
        val facing = facingOf(level, interfacePos) ?: return null

        val (dx, dy, dz) = AsyncStructures.interfaceWorldOffset(facing)
        val factoryPos = interfacePos.offset(-dx, -dy, -dz)
        if (kindOf(level, factoryPos) != AsyncBlockKind.FACTORY) return null
        if (facingOf(level, factoryPos) != facing) return null

        var count = 0
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        for (y in 0 until AsyncStructures.height(AsyncStructureType.MODULE)) {
            for (x in 0 until AsyncStructures.width(AsyncStructureType.MODULE)) {
                for (z in 0 until AsyncStructures.depth(AsyncStructureType.MODULE, 0)) {
                    val expected = AsyncStructures.blockAt(AsyncStructureType.MODULE, 0, x, y, z)
                        ?: continue
                    val (ox, oy, oz) = AsyncStructures.worldOffset(AsyncStructureType.MODULE, facing, x, y, z)
                    val pos = factoryPos.offset(ox, oy, oz)
                    val actual = kindOf(level, pos)
                    if (actual == null || !AsyncStructures.isValidCell(AsyncStructureType.MODULE, 0, x, y, z, actual)) {
                        return null
                    }
                    count++
                    minX = minOf(minX, pos.x)
                    minY = minOf(minY, pos.y)
                    minZ = minOf(minZ, pos.z)
                    maxX = maxOf(maxX, pos.x)
                    maxY = maxOf(maxY, pos.y)
                    maxZ = maxOf(maxZ, pos.z)
                }
            }
        }
        return AsyncModuleCluster(
            factoryPos,
            interfacePos,
            BlockPos(minX, minY, minZ),
            BlockPos(maxX, maxY, maxZ),
            count,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Switch (19 x 7 x (11 + 6N)) + its modules
    // ---------------------------------------------------------------------------------------------

    fun detectSwitch(level: ServerLevel, controllerPos: BlockPos): AsyncSwitchCluster? {
        if (kindOf(level, controllerPos) != AsyncBlockKind.SWITCH) return null
        val facing = facingOf(level, controllerPos) ?: return null

        for (extensions in 0..AsyncStructures.MAX_EXTENSIONS) {
            val scan = scanStructure(level, controllerPos, facing, AsyncStructureType.SWITCH, extensions)
            if (!scan.valid) continue
            if (scan.wanConnectors.size > 1 || scan.lanConnectors.size > 2) return null

            val cluster = AsyncSwitchCluster(
                controllerPos,
                scan.min,
                scan.max,
                scan.blockCount,
                scan.meConnectors,
                scan.wanConnectors,
                scan.lanConnectors,
                scan.interfaces,
            )
            for (iface in scan.interfaces) {
                detectModule(level, iface)?.let(cluster::addModule)
            }
            return cluster
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Processor (19 x 15 x (19 + 6N)) + its modules + linked switches
    // ---------------------------------------------------------------------------------------------

    fun detectProcessor(level: ServerLevel, controllerPos: BlockPos): AsyncProcessorCluster? {
        if (kindOf(level, controllerPos) != AsyncBlockKind.CONTROLLER) return null
        val facing = facingOf(level, controllerPos) ?: return null

        for (extensions in 0..AsyncStructures.MAX_EXTENSIONS) {
            val scan = scanStructure(level, controllerPos, facing, AsyncStructureType.PROCESSOR, extensions)
            if (!scan.valid) continue
            if (scan.meConnectors.size > 1 || scan.lanConnectors.size > 2) return null

            val cluster = AsyncProcessorCluster(
                controllerPos,
                scan.min,
                scan.max,
                scan.blockCount,
                scan.storageBytes,
                scan.meConnectors.size + scan.lanConnectors.size,
                scan.meConnectors,
                scan.wanConnectors,
                scan.lanConnectors,
                scan.interfaces,
            )
            for (iface in scan.interfaces) {
                detectModule(level, iface)?.let(cluster::addModule)
            }
            linkSwitches(level, cluster)
            return cluster
        }
        return null
    }

    /**
     * 从处理器的 LAN 连接器出发，沿专用线缆级联到交换机上的 WAN 连接器，再经由
     * 每台交换机自身的 LAN 连接器递归展开。分叉线缆被拒绝。
     *
     * Cascades from the processor's LAN connectors through dedicated cable to WAN connectors on
     * switches, then recursively through each switch's own LAN connectors. Forked cables are rejected.
     */
    fun linkSwitches(level: ServerLevel, cluster: AsyncProcessorCluster) {
        val seen = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.addAll(cluster.lanConnectorPositions)
        while (queue.isNotEmpty()) {
            val lan = queue.removeFirst()
            if (!seen.add(lan)) continue
            val wan = followCableToWan(level, lan) ?: continue
            if (kindOf(level, wan) != AsyncBlockKind.WAN_CONNECTOR) continue
            val switch = detectSwitchContaining(level, wan) ?: continue
            cluster.addSwitch(switch)
            for (switchLan in switch.lanConnectorPositions) {
                queue.add(switchLan)
            }
        }
    }

    /** 从 [from] 沿专用线缆追踪，直到远端找到 WAN 连接器。 / Follows dedicated cable from [from] until a WAN connector is found at the far end. */
    private fun followCableToWan(level: ServerLevel, from: BlockPos): BlockPos? =
        followCable(level, from, AsyncBlockKind.WAN_CONNECTOR)

    /** 从 WAN 连接器沿专用线缆追踪到远端的 LAN 连接器。 / Follows dedicated cable from a WAN connector to the LAN connector at the far end. */
    fun followCableFromWan(level: ServerLevel, from: BlockPos): BlockPos? =
        followCable(level, from, AsyncBlockKind.LAN_CONNECTOR)

    private fun isCableOrConnector(kind: AsyncBlockKind?): Boolean = when (kind) {
        AsyncBlockKind.CABLE,
        AsyncBlockKind.ME_CONNECTOR,
        AsyncBlockKind.WAN_CONNECTOR,
        AsyncBlockKind.LAN_CONNECTOR -> true
        else -> false
    }

    private fun followCable(level: ServerLevel, from: BlockPos, target: AsyncBlockKind): BlockPos? {
        val seen = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.add(from)
        var found: BlockPos? = null
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (!seen.add(pos)) continue
            val kindHere = kindOf(level, pos)
            var links = 0
            for (dir in Direction.entries) {
                val neighbor = pos.offset(dir.normal)
                when (val kind = kindOf(level, neighbor)) {
                    AsyncBlockKind.CABLE -> {
                        links++
                        if (neighbor !in seen) queue.add(neighbor)
                    }
                    AsyncBlockKind.ME_CONNECTOR, AsyncBlockKind.WAN_CONNECTOR, AsyncBlockKind.LAN_CONNECTOR -> {
                        links++
                        if (kind == target && neighbor != from) {
                            if (found != null && found != neighbor) return null
                            found = neighbor
                        }
                    }
                    else -> {}
                }
            }
            if (kindHere == AsyncBlockKind.CABLE && links > 2) return null
        }
        return found
    }

    /**
     * 从 [pos] 及其邻居沿专用线缆收集所有连接器（不分叉检查），用于通知上游重扫。
     * 线缆被拆除后 [pos] 已是空气，所以必须看邻居。
     *
     * Collects every connector reachable by dedicated cable from [pos] and its neighbours (no fork
     * check), for upstream rescan notification. After a cable is removed [pos] is air, so neighbours
     * must be inspected.
     */
    fun notifyHostsAlongCable(level: ServerLevel, pos: BlockPos) {
        val starts = ArrayList<BlockPos>(7)
        starts.add(pos)
        for (dir in Direction.entries) {
            starts.add(pos.offset(dir.normal))
        }
        val connectors = LinkedHashSet<BlockPos>()
        val visited = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        for (start in starts) {
            if (!isCableOrConnector(kindOf(level, start))) continue
            queue.add(start)
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (!visited.add(cur)) continue
            val kind = kindOf(level, cur)
            if (kind == AsyncBlockKind.ME_CONNECTOR || kind == AsyncBlockKind.WAN_CONNECTOR || kind == AsyncBlockKind.LAN_CONNECTOR) {
                connectors.add(cur)
            }
            for (dir in Direction.entries) {
                val neighbor = cur.offset(dir.normal)
                if (neighbor in visited) continue
                if (isCableOrConnector(kindOf(level, neighbor))) queue.add(neighbor)
            }
        }
        val hosts = LinkedHashSet<IAsyncStructureHost>()
        for (connector in connectors) {
            findHostController(level, connector)?.let(hosts::add)
        }
        for (host in hosts) {
            host.requestRescan()
        }
    }

    /**
     * 找到缓存结构边界包含 [pos]（模块接口或连接器）的已成形交换机或处理器
     * 控制器。用于把重扫通知向上游路由。
     *
     * Locates the formed switch or processor controller whose cached structure bounds contain
     * [pos] (a module interface or a connector). Used to route rescan notifications upstream.
     */
    fun findHostController(level: ServerLevel, pos: BlockPos): IAsyncStructureHost? {
        for (dy in -10..10) {
            for (dx in -12..12) {
                for (dz in -12..12) {
                    val candidate = pos.offset(dx, dy, dz)
                    val be = level.getBlockEntity(candidate) as? AsyncStructureBlockEntity ?: continue
                    val contains = when (be.kind) {
                        AsyncBlockKind.SWITCH -> be.getSwitchCluster()?.boundsContain(pos) == true
                        AsyncBlockKind.CONTROLLER -> be.getProcessorCluster()?.boundsContain(pos) == true
                        else -> false
                    }
                    if (contains) {
                        return be
                    }
                }
            }
        }
        return extraFinder?.invoke(level, pos)
    }

    /**
     * 找到结构包含 [memberPos]（WAN 连接器）的交换机并检测它。交换机控制器
     * 总在核心内，所以有界搜索即可。
     *
     * Locates the switch whose structure contains [memberPos] (a WAN connector) and detects it. The
     * switch controller always sits within the core, so a bounded search suffices.
     */
    private fun detectSwitchContaining(level: ServerLevel, memberPos: BlockPos): AsyncSwitchCluster? {
        for (dy in -8..8) {
            for (dx in -10..10) {
                for (dz in -10..10) {
                    val pos = memberPos.offset(dx, dy, dz)
                    if (kindOf(level, pos) != AsyncBlockKind.SWITCH) continue
                    val cluster = detectSwitch(level, pos) ?: continue
                    val min = cluster.boundsMin
                    val max = cluster.boundsMax
                    if (memberPos.x in min.x..max.x && memberPos.y in min.y..max.y && memberPos.z in min.z..max.z) {
                        return cluster
                    }
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Generic structure validation
    // ---------------------------------------------------------------------------------------------

    private class StructureScan(
        val valid: Boolean,
        val min: BlockPos = BlockPos.ZERO,
        val max: BlockPos = BlockPos.ZERO,
        val blockCount: Int = 0,
        val storageBytes: Long = 0,
        val meConnectors: List<BlockPos> = emptyList(),
        val wanConnectors: List<BlockPos> = emptyList(),
        val lanConnectors: List<BlockPos> = emptyList(),
        val interfaces: List<BlockPos> = emptyList(),
    )

    /**
     * 从其控制器 [anchorPos] 出发校验整座结构，返回方块数、连接器与接口位置，
     * 以及所有必需格子的边界。无关格子被跳过；必需空气格必须为空；必需方块格
     * 必须通过 [AsyncStructures.isValidCell]。
     *
     * Validates a whole structure from its controller [anchorPos]. Returns the block counts, the
     * connector and interface positions, and the bounds of all required cells. A cell is skipped when
     * it is a don't-care cell; required-air cells must be empty; required-block cells must match via
     * [AsyncStructures.isValidCell].
     */
    private fun scanStructure(
        level: ServerLevel,
        anchorPos: BlockPos,
        facing: Direction,
        type: AsyncStructureType,
        extensions: Int,
    ): StructureScan {
        val w = AsyncStructures.width(type)
        val h = AsyncStructures.height(type)
        val d = AsyncStructures.depth(type, extensions)
        var blockCount = 0
        var storageBytes = 0L
        val meConnectors = ArrayList<BlockPos>()
        val wanConnectors = ArrayList<BlockPos>()
        val lanConnectors = ArrayList<BlockPos>()
        val interfaces = ArrayList<BlockPos>()
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (y in 0 until h) {
            for (z in 0 until d) {
                for (x in 0 until w) {
                    if (AsyncStructures.isDontCare(type, x, y, z)) continue
                    val (ox, oy, oz) = AsyncStructures.worldOffset(type, facing, x, y, z)
                    val pos = anchorPos.offset(ox, oy, oz)
                    val expected = AsyncStructures.blockAt(type, extensions, x, y, z)
                    if (expected == null) {
                        if (!level.getBlockState(pos).isAir) return StructureScan(false)
                        continue
                    }
                    val actual = kindOf(level, pos)
                    if (actual == null || !AsyncStructures.isValidCell(type, extensions, x, y, z, actual)) {
                        return StructureScan(false)
                    }
                    blockCount++
                    minX = minOf(minX, pos.x)
                    minY = minOf(minY, pos.y)
                    minZ = minOf(minZ, pos.z)
                    maxX = maxOf(maxX, pos.x)
                    maxY = maxOf(maxY, pos.y)
                    maxZ = maxOf(maxZ, pos.z)
                    when (actual) {
                        AsyncBlockKind.STORAGE -> storageBytes += actual.storageBytes
                        AsyncBlockKind.ME_CONNECTOR -> meConnectors.add(pos)
                        AsyncBlockKind.WAN_CONNECTOR -> wanConnectors.add(pos)
                        AsyncBlockKind.LAN_CONNECTOR -> lanConnectors.add(pos)
                        AsyncBlockKind.MODULE_INTERFACE -> interfaces.add(pos)
                        else -> {}
                    }
                }
            }
        }
        return StructureScan(
            valid = true,
            min = BlockPos(minX, minY, minZ),
            max = BlockPos(maxX, maxY, maxZ),
            blockCount = blockCount,
            storageBytes = storageBytes,
            meConnectors = meConnectors,
            wanConnectors = wanConnectors,
            lanConnectors = lanConnectors,
            interfaces = interfaces,
        )
    }
}
