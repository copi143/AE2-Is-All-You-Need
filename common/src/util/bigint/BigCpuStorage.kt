package allyouneed.util.bigint

import allyouneed.util.bigStorage
import allyouneed.util.isUnboundedCapacity
import appeng.api.networking.crafting.ICraftingCPU
import appeng.me.cluster.implementations.CraftingCPUCluster
import java.math.BigInteger

/**
 * Helpers for [allyouneed.api.BigCpuCapacity] on [CraftingCPUCluster].
 * Capacity lives on the cluster instance (mixin fields), not a global map.
 */
object BigCpuStorage {
    @JvmStatic
    fun canHold(cluster: CraftingCPUCluster?, jobBytes: Long): Boolean {
        if (cluster == null) return false
        if (cluster.isUnboundedCapacity) return true
        if (jobBytes <= 0) return true
        return cluster.bigStorage >= BigInteger.valueOf(jobBytes)
    }

    @JvmStatic
    fun canHold(cpu: ICraftingCPU?, jobBytes: Long): Boolean {
        if (cpu is CraftingCPUCluster) return canHold(cpu, jobBytes)
        if (cpu == null) return false
        if (jobBytes <= 0) return true
        return cpu.availableStorage >= jobBytes
    }

    @JvmStatic
    fun compareStorage(a: CraftingCPUCluster, b: CraftingCPUCluster): Int {
        when {
            a.isUnboundedCapacity && b.isUnboundedCapacity -> return 0
            a.isUnboundedCapacity -> return 1
            b.isUnboundedCapacity -> return -1
        }
        val sa = if (a.bigStorage.signum() == 0) BigInteger.valueOf(a.availableStorage) else a.bigStorage
        val sb = if (b.bigStorage.signum() == 0) BigInteger.valueOf(b.availableStorage) else b.bigStorage
        return sa.compareTo(sb)
    }
}
