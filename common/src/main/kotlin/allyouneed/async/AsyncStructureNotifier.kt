package allyouneed.async

import net.minecraft.core.BlockPos
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerLevel

/**
 * Reacts to structural blocks (frame, machine block, glass, tower, cores, cable) being placed or
 * removed. Structural blocks carry no block entity, so nothing else revalidates the surrounding
 * multiblock when they change - which leaves manual builds unformed and broken structures stuck
 * formed. On a structural change we scan a box around the position and ask the affected plain
 * controllers to revalidate immediately instead of waiting for a poll.
 *
 * GT controllers are deliberately not notified here: once formed they are invalidated cell by cell
 * by GTCEu's LevelMixin (the pattern's position cache), and unformed ones are formed by the GT
 * async poller every 4 ticks.
 *
 * The scan is deferred to the next server tick and coalesced (at most one scan per
 * [SCAN_INTERVAL] per level), so rapid placements such as a GT one-click build cost a single scan
 * around the latest changed position.
 */
object AsyncStructureNotifier {

    private const val RX = 24
    private const val RY = 12
    private const val RZ = 24

    /** Minimum server ticks between two scans of the same level (coalesces build bursts). */
    private const val SCAN_INTERVAL = 4

    private val pending = HashMap<ServerLevel, BlockPos>()
    private val lastScanTick = HashMap<ServerLevel, Int>()
    private val scheduled = HashSet<ServerLevel>()

    fun onStructuralBlockChanged(level: ServerLevel, pos: BlockPos) {
        pending[level] = pos
        if (scheduled.add(level)) {
            level.server.tell(TickTask(0) { flush(level) })
        }
    }

    private fun flush(level: ServerLevel) {
        scheduled.remove(level)
        val pos = pending.remove(level) ?: return
        val last = lastScanTick[level]
        val now = level.server.tickCount
        if (last != null && now - last < SCAN_INTERVAL) {
            pending[level] = pos
            if (scheduled.add(level)) {
                level.server.tell(TickTask(0) { flush(level) })
            }
            return
        }
        lastScanTick[level] = now
        scan(level, pos)
    }

    private fun scan(level: ServerLevel, pos: BlockPos) {
        val minY = (pos.y - RY).coerceAtLeast(level.minBuildHeight)
        val maxY = (pos.y + RY).coerceAtMost(level.maxBuildHeight - 1)
        for (y in minY..maxY) {
            for (z in (pos.z - RZ)..(pos.z + RZ)) {
                for (x in (pos.x - RX)..(pos.x + RX)) {
                    val be = level.getBlockEntity(BlockPos(x, y, z)) ?: continue
                    if (be is AsyncStructureBlockEntity) {
                        when (be.kind) {
                            AsyncBlockKind.MODULE_INTERFACE,
                            AsyncBlockKind.SWITCH,
                            AsyncBlockKind.CONTROLLER -> be.requestRescan()
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
