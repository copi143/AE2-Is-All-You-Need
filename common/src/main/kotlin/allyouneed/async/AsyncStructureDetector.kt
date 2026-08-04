package allyouneed.async

import allyouneed.multiblock.AsyncStructureType
import allyouneed.multiblock.AsyncStructures
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.properties.BlockStateProperties

/**
 * Anchor-based detector for the three async synthesis structures.
 *
 * The shape truth lives in [AsyncStructures]. Detection starts at a controller anchor, derives the
 * structure orientation from the controller's facing, probes the extension count (0..[AsyncStructures.MAX_EXTENSIONS])
 * and validates every in-bounds cell, distinguishing:
 *
 *  - required blocks ([AsyncStructures.blockAt] returns a kind, validated via [AsyncStructures.isValidCell])
 *  - required air ([AsyncStructures.blockAt] returns null, the cell must be empty)
 *  - don't-care cells ([AsyncStructures.isDonCare]) which accept anything
 *
 * A module is detected from its floor interface (Z) upward (3x7x5); a switch scans the modules on
 * its extension-bay interfaces; the processor scans its own modules plus the switches reachable
 * through LAN/WAN connectors and dedicated cable (directly or cascaded).
 */
object AsyncStructureDetector {

    fun kindOf(level: ServerLevel, pos: BlockPos): AsyncBlockKind? {
        val block = level.getBlockState(pos).block
        return (block as? IAsyncKindBlock)?.kind
    }

    fun facingOf(level: ServerLevel, pos: BlockPos): Direction? {
        val state = level.getBlockState(pos)
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        }
        // GT machine blocks use the same horizontal-facing property by default, but a machine with
        // an extended rotation state exposes its facing through GT's upwards-facing property.
        if (state.hasProperty(GTBlockStateProperties.UPWARDS_FACING)) {
            return state.getValue(GTBlockStateProperties.UPWARDS_FACING)
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Module (3 x 7 x 5), mounted on a floor interface (Z)
    // ---------------------------------------------------------------------------------------------

    /**
     * Detects the module built on the [interfacePos] floor interface. The interface block's facing
     * fixes the module orientation; the factory controller is found directly below/behind it and must
     * face the same way. Every one of the 3x7x5 cells is required.
     */
    fun detectModule(level: ServerLevel, interfacePos: BlockPos): AsyncModuleCluster? {
        if (kindOf(level, interfacePos) != AsyncBlockKind.MODULE_INTERFACE) return null
        if (level.getBlockEntity(interfacePos) !is AsyncStructureBlockEntity) return null
        val facing = facingOf(level, interfacePos) ?: return null

        val factoryPos = interfacePos.offset(-2 * facing.stepX, 4, -2 * facing.stepZ)
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
                    val (dx, dy, dz) = AsyncStructures.worldOffset(AsyncStructureType.MODULE, facing, x, y, z)
                    val pos = factoryPos.offset(dx, dy, dz)
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
            for (interfacePos in scan.interfaces) {
                detectModule(level, interfacePos)?.let(cluster::addModule)
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
            for (interfacePos in scan.interfaces) {
                detectModule(level, interfacePos)?.let(cluster::addModule)
            }
            linkSwitches(level, cluster)
            return cluster
        }
        return null
    }

    /**
     * Cascades from the processor's LAN connectors through dedicated cable to WAN connectors on
     * switches, then recursively through each switch's own LAN connectors.
     */
    private fun linkSwitches(level: ServerLevel, cluster: AsyncProcessorCluster) {
        val seen = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.addAll(cluster.lanConnectorPositions)
        while (queue.isNotEmpty()) {
            val lan = queue.removeFirst()
            if (!seen.add(lan)) continue
            val wan = followCableToWan(level, lan) ?: continue
            if (kindOf(level, wan) != AsyncBlockKind.WAN_CONNECTOR) continue
            val switch = detectSwitchContaining(level, wan) ?: continue
            if (switch.isDestroyed) continue
            cluster.addSwitch(switch)
            for (switchLan in switch.lanConnectorPositions) {
                queue.add(switchLan)
            }
        }
    }

    /** Follows dedicated cable from [from] until a WAN connector is found at the far end. */
    private fun followCableToWan(level: ServerLevel, from: BlockPos): BlockPos? =
        followCable(level, from, AsyncBlockKind.WAN_CONNECTOR)

    /** Follows dedicated cable from a WAN connector to the LAN connector at the far end. */
    fun followCableFromWan(level: ServerLevel, from: BlockPos): BlockPos? =
        followCable(level, from, AsyncBlockKind.LAN_CONNECTOR)

    private fun followCable(level: ServerLevel, from: BlockPos, target: AsyncBlockKind): BlockPos? {
        val seen = HashSet<BlockPos>()
        val stack = ArrayDeque<BlockPos>()
        stack.add(from)
        while (stack.isNotEmpty()) {
            val pos = stack.removeFirst()
            if (!seen.add(pos)) continue
            for (dir in Direction.entries) {
                val neighbor = pos.offset(dir.normal)
                if (seen.contains(neighbor)) continue
                when (val kind = kindOf(level, neighbor)) {
                    AsyncBlockKind.CABLE -> stack.add(neighbor)
                    AsyncBlockKind.ME_CONNECTOR, AsyncBlockKind.WAN_CONNECTOR, AsyncBlockKind.LAN_CONNECTOR ->
                        if (kind == target) return neighbor
                    else -> {}
                }
            }
        }
        return null
    }

    /**
     * Locates the formed switch or processor controller whose cached structure bounds contain
     * [pos] (a module interface or a connector). Used to route rescan notifications upstream.
     */
    fun findHostController(level: ServerLevel, pos: BlockPos): AsyncStructureBlockEntity? {
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
        return null
    }

    /**
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
                    if (AsyncStructures.isDonCare(type, x, y, z)) continue
                    val (dx, dy, dz) = AsyncStructures.worldOffset(type, facing, x, y, z)
                    val pos = anchorPos.offset(dx, dy, dz)
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
