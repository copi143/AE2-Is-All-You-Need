package allyouneed.util.bigint

import allyouneed.util.IecFormat
import allyouneed.util.saturateToLong
import appeng.api.networking.crafting.ICraftingCPU
import appeng.me.cluster.implementations.CraftingCPUCluster
import java.math.BigInteger
import java.util.*

/** Side-channel BigInteger (and unbounded) capacity for crafting CPU clusters and UI list entries. */
object BigCpuStorage {
    private val CLUSTER_STORAGE: MutableMap<CraftingCPUCluster, BigInteger> = Collections.synchronizedMap(WeakHashMap())
    private val CLUSTER_UNBOUNDED: MutableMap<CraftingCPUCluster, Boolean> = Collections.synchronizedMap(WeakHashMap())

    /** UI / packet entries keyed by serial or entry identity. */
    private val ENTRY_STORAGE: MutableMap<Any, BigInteger> = Collections.synchronizedMap(WeakHashMap())
    private val ENTRY_UNBOUNDED: MutableMap<Any, Boolean> = Collections.synchronizedMap(WeakHashMap())

    @JvmStatic
    fun clearCluster(cluster: CraftingCPUCluster?) {
        if (cluster == null) return
        CLUSTER_STORAGE.remove(cluster)
        CLUSTER_UNBOUNDED.remove(cluster)
    }

    @JvmStatic
    fun hasClusterEntry(cluster: CraftingCPUCluster?): Boolean =
        cluster != null && (CLUSTER_STORAGE.containsKey(cluster) || CLUSTER_UNBOUNDED.containsKey(cluster))

    @JvmStatic
    fun addClusterBytes(cluster: CraftingCPUCluster?, bytes: BigInteger?, unbounded: Boolean) {
        if (cluster == null) return
        if (unbounded) {
            CLUSTER_UNBOUNDED[cluster] = true
            CLUSTER_STORAGE[cluster] = BigInteger.valueOf(Long.MAX_VALUE)
            return
        }
        if (isUnbounded(cluster)) return
        if (bytes == null || bytes.signum() <= 0) return
        CLUSTER_STORAGE.merge(cluster, bytes, BigInteger::add)
    }

    @JvmStatic
    fun isUnbounded(cluster: CraftingCPUCluster?): Boolean = cluster != null && CLUSTER_UNBOUNDED[cluster] == true

    @JvmStatic
    fun isUnbounded(cpu: ICraftingCPU?): Boolean = cpu is CraftingCPUCluster && isUnbounded(cpu)

    @JvmStatic
    fun getClusterStorage(cluster: CraftingCPUCluster?): BigInteger {
        if (cluster == null) return BigInteger.ZERO
        if (isUnbounded(cluster)) return BigInteger.valueOf(Long.MAX_VALUE)
        return CLUSTER_STORAGE[cluster] ?: BigInteger.ZERO
    }

    @JvmStatic
    fun getClusterStorageLong(cluster: CraftingCPUCluster?): Long {
        if (isUnbounded(cluster)) return Long.MAX_VALUE
        return getClusterStorage(cluster).saturateToLong()
    }

    /** Whether [cluster] can hold a job of [jobBytes] bytes. */
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
    fun setEntryStorage(entry: Any?, amount: BigInteger?, unbounded: Boolean) {
        if (entry == null) return
        if (unbounded) {
            ENTRY_UNBOUNDED[entry] = true
            ENTRY_STORAGE[entry] = BigInteger.valueOf(Long.MAX_VALUE)
            return
        }
        ENTRY_UNBOUNDED.remove(entry)
        if (amount == null) {
            ENTRY_STORAGE.remove(entry)
        } else {
            ENTRY_STORAGE[entry] = amount
        }
    }

    @JvmStatic
    fun isEntryUnbounded(entry: Any?): Boolean = entry != null && ENTRY_UNBOUNDED[entry] == true

    @JvmStatic
    fun getEntryStorage(entry: Any?, fallbackLong: Long): BigInteger {
        if (entry == null) return BigInteger.valueOf(maxOf(0L, fallbackLong))
        if (isEntryUnbounded(entry)) return BigInteger.valueOf(Long.MAX_VALUE)
        return ENTRY_STORAGE[entry] ?: BigInteger.valueOf(maxOf(0L, fallbackLong))
    }

    @JvmStatic
    fun hasEntryStorage(entry: Any?): Boolean =
        entry != null && (ENTRY_STORAGE.containsKey(entry) || ENTRY_UNBOUNDED.containsKey(entry))

    /** Binary-unit short label for CPU UI or ∞ when unbounded. */
    @JvmStatic
    fun formatStorageLabel(entry: Any?, fallbackLong: Long): String {
        if (isEntryUnbounded(entry)) return "∞"
        return formatBinaryBytes(getEntryStorage(entry, fallbackLong))
    }

    @JvmStatic
    fun formatStorageLabel(cluster: CraftingCPUCluster?): String {
        if (isUnbounded(cluster)) return "∞"
        return formatBinaryBytes(getClusterStorage(cluster))
    }

    @JvmStatic
    fun formatBinaryBytes(bytes: BigInteger?): String {
        if (bytes == null || bytes.signum() <= 0) return "0"
        return IecFormat.formatBytes(bytes)
    }
}
