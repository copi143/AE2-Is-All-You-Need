package allyouneed.util;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Side-channel BigInteger (and unbounded) capacity for crafting CPU clusters and UI list entries.
 */
public final class BigCpuStorage {
    private static final Map<CraftingCPUCluster, BigInteger> CLUSTER_STORAGE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<CraftingCPUCluster, Boolean> CLUSTER_UNBOUNDED =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** UI / packet entries keyed by serial or entry identity. */
    private static final Map<Object, BigInteger> ENTRY_STORAGE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> ENTRY_UNBOUNDED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BigCpuStorage() {
    }

    public static void clearCluster(CraftingCPUCluster cluster) {
        if (cluster == null) {
            return;
        }
        CLUSTER_STORAGE.remove(cluster);
        CLUSTER_UNBOUNDED.remove(cluster);
    }

    public static boolean hasClusterEntry(CraftingCPUCluster cluster) {
        return cluster != null
                && (CLUSTER_STORAGE.containsKey(cluster) || CLUSTER_UNBOUNDED.containsKey(cluster));
    }

    public static void addClusterBytes(CraftingCPUCluster cluster, BigInteger bytes, boolean unbounded) {
        if (cluster == null) {
            return;
        }
        if (unbounded) {
            CLUSTER_UNBOUNDED.put(cluster, true);
            CLUSTER_STORAGE.put(cluster, BigInteger.valueOf(Long.MAX_VALUE));
            return;
        }
        if (isUnbounded(cluster)) {
            return;
        }
        if (bytes == null || bytes.signum() <= 0) {
            return;
        }
        CLUSTER_STORAGE.merge(cluster, bytes, BigInteger::add);
    }

    public static boolean isUnbounded(CraftingCPUCluster cluster) {
        return cluster != null && Boolean.TRUE.equals(CLUSTER_UNBOUNDED.get(cluster));
    }

    public static boolean isUnbounded(ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster cluster) {
            return isUnbounded(cluster);
        }
        return false;
    }

    public static BigInteger getClusterStorage(CraftingCPUCluster cluster) {
        if (cluster == null) {
            return BigInteger.ZERO;
        }
        if (isUnbounded(cluster)) {
            return BigInteger.valueOf(Long.MAX_VALUE);
        }
        BigInteger big = CLUSTER_STORAGE.get(cluster);
        if (big != null) {
            return big;
        }
        return BigInteger.ZERO;
    }

    public static long getClusterStorageLong(CraftingCPUCluster cluster) {
        if (isUnbounded(cluster)) {
            return Long.MAX_VALUE;
        }
        return SiFormat.saturateToLong(getClusterStorage(cluster));
    }

    /**
     * @return true if {@code storage} can hold a job of {@code jobBytes} bytes
     */
    public static boolean canHold(CraftingCPUCluster cluster, long jobBytes) {
        if (cluster == null) {
            return false;
        }
        if (isUnbounded(cluster)) {
            return true;
        }
        if (jobBytes <= 0) {
            return true;
        }
        return getClusterStorage(cluster).compareTo(BigInteger.valueOf(jobBytes)) >= 0;
    }

    public static boolean canHold(ICraftingCPU cpu, long jobBytes) {
        if (cpu instanceof CraftingCPUCluster cluster) {
            return canHold(cluster, jobBytes);
        }
        if (cpu == null) {
            return false;
        }
        if (jobBytes <= 0) {
            return true;
        }
        long available = cpu.getAvailableStorage();
        return available >= jobBytes;
    }

    public static int compareStorage(CraftingCPUCluster a, CraftingCPUCluster b) {
        boolean ua = isUnbounded(a);
        boolean ub = isUnbounded(b);
        if (ua != ub) {
            // unbounded is "larger"
            return ua ? 1 : -1;
        }
        if (ua) {
            return 0;
        }
        BigInteger sa = hasClusterEntry(a) ? getClusterStorage(a) : BigInteger.valueOf(Math.max(0L, a.getAvailableStorage()));
        BigInteger sb = hasClusterEntry(b) ? getClusterStorage(b) : BigInteger.valueOf(Math.max(0L, b.getAvailableStorage()));
        return sa.compareTo(sb);
    }

    public static void setEntryStorage(Object entry, @Nullable BigInteger amount, boolean unbounded) {
        if (entry == null) {
            return;
        }
        if (unbounded) {
            ENTRY_UNBOUNDED.put(entry, true);
            ENTRY_STORAGE.put(entry, BigInteger.valueOf(Long.MAX_VALUE));
            return;
        }
        ENTRY_UNBOUNDED.remove(entry);
        if (amount == null) {
            ENTRY_STORAGE.remove(entry);
        } else {
            ENTRY_STORAGE.put(entry, amount);
        }
    }

    public static boolean isEntryUnbounded(Object entry) {
        return entry != null && Boolean.TRUE.equals(ENTRY_UNBOUNDED.get(entry));
    }

    public static BigInteger getEntryStorage(Object entry, long fallbackLong) {
        if (entry == null) {
            return BigInteger.valueOf(Math.max(0L, fallbackLong));
        }
        if (isEntryUnbounded(entry)) {
            return BigInteger.valueOf(Long.MAX_VALUE);
        }
        BigInteger big = ENTRY_STORAGE.get(entry);
        if (big != null) {
            return big;
        }
        return BigInteger.valueOf(Math.max(0L, fallbackLong));
    }

    public static boolean hasEntryStorage(Object entry) {
        return entry != null && (ENTRY_STORAGE.containsKey(entry) || ENTRY_UNBOUNDED.containsKey(entry));
    }

    /** Binary-unit short label for CPU UI (1k, 4m, …) or ∞ when unbounded. */
    public static String formatStorageLabel(Object entry, long fallbackLong) {
        if (isEntryUnbounded(entry)) {
            return "\u221E";
        }
        BigInteger bytes = getEntryStorage(entry, fallbackLong);
        return formatBinaryBytes(bytes);
    }

    public static String formatStorageLabel(CraftingCPUCluster cluster) {
        if (isUnbounded(cluster)) {
            return "\u221E";
        }
        return formatBinaryBytes(getClusterStorage(cluster));
    }

    public static String formatBinaryBytes(BigInteger bytes) {
        if (bytes == null || bytes.signum() <= 0) {
            return "0";
        }
        return IecFormat.formatBytes(bytes);
    }
}
