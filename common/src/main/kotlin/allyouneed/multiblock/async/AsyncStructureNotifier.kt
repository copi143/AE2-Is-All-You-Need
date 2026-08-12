package allyouneed.multiblock.async

import allyouneed.multiblock.async.AsyncStructureNotifier.SCAN_INTERVAL
import net.minecraft.core.BlockPos
import net.minecraft.server.TickTask
import net.minecraft.server.level.ServerLevel

/**
 * 响应结构方块（框架、机器方块、玻璃、塔、核心、线缆）的放置与移除。
 * 结构方块没有方块实体，所以它们变化时没有别的东西会重新校验周围的多方块——
 * 这会让手动搭建的结构一直不成形、已被破坏的结构一直卡在成形状态。结构变化时，
 * 我们扫描该位置周围的盒子，并让受影响的自有控制器立即重新校验，而不是等待轮询。
 *
 * GT 控制器在这里刻意不通知：成形后它们会被 GTCEu 的 LevelMixin（pattern 的
 * 位置缓存）逐格失效；未成形的则由 GT async 轮询器每 4 tick 成形。
 *
 * 扫描被推迟到下一个服务端 tick，并进行合并（每 [SCAN_INTERVAL] 每世界至多一次），
 * 因此像 GT 一键建造那样的大量放置只会产生围绕最近变化位置的一次扫描。
 *
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

    /** 同一世界两次扫描之间的最小服务端 tick 数（合并建造爆发）。 / Minimum server ticks between two scans of the same level (coalesces build bursts). */
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
