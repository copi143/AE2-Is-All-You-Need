package allyouneed.util.bigint

import allyouneed.util.MetricFormat
import allyouneed.util.bigStorage
import allyouneed.util.isUnboundedCapacity
import allyouneed.util.saturateToLong
import appeng.api.networking.crafting.ICraftingCPU
import appeng.me.cluster.implementations.CraftingCPUCluster
import java.math.BigInteger

/**
 * Helpers for [allyouneed.api.BigCpuCapacity] on [CraftingCPUCluster].
 * Capacity lives on the cluster instance (mixin fields), not a global map.
 */
object BigCpuStorage {
    @JvmStatic
    fun hasClusterEntry(cluster: CraftingCPUCluster): Boolean {
        return cluster.isUnboundedCapacity || cluster.bigStorage.signum() > 0
    }

    @JvmStatic
    fun getClusterStorage(cluster: CraftingCPUCluster): BigInteger {
        if (cluster.isUnboundedCapacity) return BigInteger.valueOf(Long.MAX_VALUE)
        return cluster.bigStorage
    }

    @JvmStatic
    fun getClusterStorageLong(cluster: CraftingCPUCluster): Long {
        if (cluster.isUnboundedCapacity) return Long.MAX_VALUE
        return getClusterStorage(cluster).saturateToLong()
    }

    @JvmStatic
    fun canHold(cluster: CraftingCPUCluster?, jobBytes: Long): Boolean {
        if (cluster == null) return false
        if (cluster.isUnboundedCapacity) return true
        if (jobBytes <= 0) return true
        return getClusterStorage(cluster) >= BigInteger.valueOf(jobBytes)
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
        val ua = a.isUnboundedCapacity
        val ub = b.isUnboundedCapacity
        if (ua != ub) return if (ua) 1 else -1
        if (ua) return 0
        val sa = if (hasClusterEntry(a)) getClusterStorage(a)
        else BigInteger.valueOf(maxOf(0L, a.availableStorage))
        val sb = if (hasClusterEntry(b)) getClusterStorage(b)
        else BigInteger.valueOf(maxOf(0L, b.availableStorage))
        return sa.compareTo(sb)
    }
}
