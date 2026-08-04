package allyouneed.util.bigint

import allyouneed.util.IntegerFormat
import allyouneed.util.saturateToLong
import appeng.api.networking.crafting.ICraftingCPU
import appeng.me.cluster.implementations.CraftingCPUCluster
import java.math.BigInteger

/**
 * Helpers for [BigCpuCapacity] on [CraftingCPUCluster].
 * Capacity lives on the cluster instance (mixin fields), not a global map.
 */
object BigCpuStorage {
    @JvmStatic
    fun clearCluster(cluster: CraftingCPUCluster?) {
        cluster as BigCpuCapacity
        cluster.bigStorage = BigInteger.ZERO
        cluster.isUnboundedCapacity = false
    }

    @JvmStatic
    fun hasClusterEntry(cluster: CraftingCPUCluster?): Boolean {
        cluster as BigCpuCapacity
        return cluster.isUnboundedCapacity || cluster.bigStorage.signum() > 0
    }

    @JvmStatic
    fun addClusterBytes(cluster: CraftingCPUCluster?, bytes: BigInteger?, unbounded: Boolean) {
        cluster as BigCpuCapacity
        if (unbounded) {
            cluster.isUnboundedCapacity = true
            cluster.bigStorage = BigInteger.valueOf(Long.MAX_VALUE)
            return
        }
        if (bytes == null || bytes.signum() <= 0 || cluster.isUnboundedCapacity) return
        cluster.bigStorage = cluster.bigStorage.add(bytes)
    }

    @JvmStatic
    fun isUnbounded(cluster: CraftingCPUCluster?): Boolean = (cluster as BigCpuCapacity).isUnboundedCapacity

    @JvmStatic
    fun isUnbounded(cpu: ICraftingCPU?): Boolean = cpu is CraftingCPUCluster && isUnbounded(cpu)

    @JvmStatic
    fun getClusterStorage(cluster: CraftingCPUCluster?): BigInteger {
        cluster as BigCpuCapacity
        if (cluster.isUnboundedCapacity) return BigInteger.valueOf(Long.MAX_VALUE)
        return cluster.bigStorage
    }

    @JvmStatic
    fun getClusterStorageLong(cluster: CraftingCPUCluster?): Long {
        if (isUnbounded(cluster)) return Long.MAX_VALUE
        return getClusterStorage(cluster).saturateToLong()
    }

    @JvmStatic
    fun canHold(cluster: CraftingCPUCluster?, jobBytes: Long): Boolean {
        if (cluster == null) return false
        if (isUnbounded(cluster)) return true
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
    fun compareStorage(a: CraftingCPUCluster?, b: CraftingCPUCluster?): Int {
        val ua = isUnbounded(a)
        val ub = isUnbounded(b)
        if (ua != ub) return if (ua) 1 else -1
        if (ua) return 0
        val sa = if (hasClusterEntry(a)) getClusterStorage(a)
        else BigInteger.valueOf(maxOf(0L, a?.availableStorage ?: 0L))
        val sb = if (hasClusterEntry(b)) getClusterStorage(b)
        else BigInteger.valueOf(maxOf(0L, b?.availableStorage ?: 0L))
        return sa.compareTo(sb)
    }

    @JvmStatic
    fun formatStorageLabel(cluster: CraftingCPUCluster?): String {
        if (isUnbounded(cluster)) return "∞"
        return formatBinaryBytes(getClusterStorage(cluster))
    }

    @JvmStatic
    fun formatBinaryBytes(bytes: BigInteger?): String {
        if (bytes == null || bytes.signum() <= 0) return "0"
        return IntegerFormat.iecFormat(bytes)
    }
}
