package allyouneed.util;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Holds BigInteger stored amounts for {@link GridInventoryEntry} and a thread-local
 * snapshot used while building ME inventory update packets.
 */
public final class BigAmounts {
    private static final Map<GridInventoryEntry, BigInteger> ENTRY_AMOUNTS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final ThreadLocal<BigKeyCounter> CURRENT = new ThreadLocal<>();

    private BigAmounts() {
    }

    public static void setEntryAmount(GridInventoryEntry entry, @Nullable BigInteger amount) {
        if (entry == null) {
            return;
        }
        if (amount == null) {
            ENTRY_AMOUNTS.remove(entry);
        } else {
            ENTRY_AMOUNTS.put(entry, amount);
        }
    }

    public static BigInteger getEntryAmount(GridInventoryEntry entry) {
        if (entry == null) {
            return BigInteger.ZERO;
        }
        BigInteger big = ENTRY_AMOUNTS.get(entry);
        if (big != null) {
            return big;
        }
        return BigInteger.valueOf(Math.max(0L, entry.getStoredAmount()));
    }

    public static boolean hasEntryAmount(GridInventoryEntry entry) {
        return entry != null && ENTRY_AMOUNTS.containsKey(entry);
    }

    public static void copyEntryAmount(GridInventoryEntry from, GridInventoryEntry to) {
        if (from == null || to == null) {
            return;
        }
        BigInteger big = ENTRY_AMOUNTS.get(from);
        if (big != null) {
            ENTRY_AMOUNTS.put(to, big);
        }
    }

    @Nullable
    public static BigKeyCounter getCurrent() {
        return CURRENT.get();
    }

    public static void setCurrent(@Nullable BigKeyCounter counter) {
        if (counter == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(counter);
        }
    }

    public static void clearCurrent() {
        CURRENT.remove();
    }

    /**
     * @return amount from the current snapshot, or {@code null} if no snapshot is active
     */
    @Nullable
    public static BigInteger getCurrentAmount(@Nullable AEKey key) {
        BigKeyCounter current = CURRENT.get();
        if (current == null || key == null) {
            return null;
        }
        return current.get(key);
    }
}
