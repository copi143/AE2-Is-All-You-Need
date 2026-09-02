package allyouneed.gtceu.multiblock

import allyouneed.multiblock.async.AsyncProcessorCluster
import allyouneed.multiblock.async.AsyncSwitchCluster
import allyouneed.multiblock.async.IAsyncStructureHost
import com.gregtechceu.gtceu.api.machine.MetaMachine
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * 在有界盒内查找已成形的 GTCEu async 控制器。由检测器的 [allyouneed.multiblock.async.AsyncStructureDetector.extraFinder]
 * 调用，避免 common 检测器直接依赖 GTCEu 类型。
 *
 * Locates a formed GTCEu async controller inside a bounded box. Invoked from the detector's
 * [allyouneed.multiblock.async.AsyncStructureDetector.extraFinder] so the common detector never
 * depends on GTCEu types.
 */
object AsyncStructureGtHosts {

    fun findNear(level: ServerLevel, pos: BlockPos): IAsyncStructureHost? {
        for (dy in -10..10) {
            for (dx in -12..12) {
                for (dz in -12..12) {
                    val candidate = pos.offset(dx, dy, dz)
                    val machine = MetaMachine.getMachine(level, candidate) as? AsyncStructureGtControllerMachine
                        ?: continue
                    val cluster = machine.getCluster() ?: continue
                    val contains = when (cluster) {
                        is AsyncSwitchCluster -> cluster.boundsContain(pos)
                        is AsyncProcessorCluster -> cluster.boundsContain(pos)
                        else -> false
                    }
                    if (contains) return machine
                }
            }
        }
        return null
    }
}
