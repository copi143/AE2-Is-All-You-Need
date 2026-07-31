package allyouneed.util;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Network-wide amount tally using {@link BigInteger} to avoid long overflow when summing cells.
 */
public final class BigKeyCounter {
    private final Object2ObjectOpenHashMap<AEKey, BigInteger> amounts = new Object2ObjectOpenHashMap<>();

    public void clear() {
        amounts.clear();
    }

    public void add(AEKey key, long amount) {
        if (amount == 0) {
            return;
        }
        add(key, BigInteger.valueOf(amount));
    }

    public void add(AEKey key, BigInteger amount) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() == 0) {
            return;
        }
        amounts.merge(key, amount, BigInteger::add);
    }

    public void addAll(KeyCounter other) {
        for (var entry : other) {
            add(entry.getKey(), entry.getLongValue());
        }
    }

    public void addAll(BigKeyCounter other) {
        for (var entry : other.amounts.object2ObjectEntrySet()) {
            add(entry.getKey(), entry.getValue());
        }
    }

    public void set(AEKey key, BigInteger amount) {
        Objects.requireNonNull(key, "key");
        if (amount == null || amount.signum() == 0) {
            amounts.remove(key);
        } else {
            amounts.put(key, amount);
        }
    }

    public BigInteger get(AEKey key) {
        Objects.requireNonNull(key, "key");
        return amounts.getOrDefault(key, BigInteger.ZERO);
    }

    public long getSaturatedLong(AEKey key) {
        return CommonKt.saturateToLong(get(key));
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public int size() {
        return amounts.size();
    }

    public Set<AEKey> keySet() {
        return Collections.unmodifiableSet(amounts.keySet());
    }

    public Object2ObjectMap.FastEntrySet<AEKey, BigInteger> entries() {
        return amounts.object2ObjectEntrySet();
    }

    public void removeZeros() {
        amounts.object2ObjectEntrySet().removeIf(e -> e.getValue().signum() == 0);
    }

    /**
     * Diff keys whose amounts differ from {@code other} (including keys only present on one side).
     */
    public void collectChangedKeys(BigKeyCounter other, java.util.function.Consumer<AEKey> out) {
        for (var entry : amounts.object2ObjectEntrySet()) {
            if (entry.getValue().compareTo(other.get(entry.getKey())) != 0) {
                out.accept(entry.getKey());
            }
        }
        for (var entry : other.amounts.object2ObjectEntrySet()) {
            if (!amounts.containsKey(entry.getKey())) {
                out.accept(entry.getKey());
            }
        }
    }

    public BigKeyCounter copy() {
        BigKeyCounter copy = new BigKeyCounter();
        copy.amounts.putAll(this.amounts);
        return copy;
    }

    /**
     * Add saturated long amounts into a KeyCounter for AE2 compatibility paths.
     */
    public void copySaturatedTo(KeyCounter out) {
        for (var entry : amounts.object2ObjectEntrySet()) {
            out.add(entry.getKey(), CommonKt.saturateToLong(entry.getValue()));
        }
    }

    @Nullable
    public static BigKeyCounter fromKeyCounter(KeyCounter counter) {
        if (counter == null) {
            return null;
        }
        BigKeyCounter big = new BigKeyCounter();
        big.addAll(counter);
        return big;
    }
}
