package it.unimi.dsi.fastutil.objects;

import java.util.function.BiFunction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Runtime guards for call sites whose callback was proven to be Integer::sum. */
public final class MapAccumulationSupport {
    private static final IdentityKeyClasses IDENTITY_KEYS = new IdentityKeyClasses();
    private static final BiFunction<Integer, Integer, Integer> SUM = Integer::sum;
    private static final boolean STATS = Boolean.getBoolean("allyouneed.mapaccumulation.stats");
    private static final ConcurrentHashMap<String, LongAdder> COUNTS = STATS ? new ConcurrentHashMap<>() : null;

    static {
        if (STATS) Runtime.getRuntime().addShutdownHook(new Thread(() -> COUNTS.forEach((name, count) ->
                System.err.println("[MapAccumulation] " + name + "=" + count.sum())), "MapAccumulationStats"));
    }

    private MapAccumulationSupport() {}

    public static boolean canOptimize(Object receiver, Object key) {
        boolean safe = receiver != null && receiver.getClass() == Object2IntOpenHashMap.class && safeKey(key);
        if (STATS) {
            String name = (safe ? "fast:" : "fallback:") + (key == null ? "null" : key.getClass().getName());
            COUNTS.computeIfAbsent(name, ignored -> new LongAdder()).increment();
        }
        return safe;
    }

    private static boolean safeKey(Object key) {
        if (key == null) return true;
        Class<?> type = key.getClass();
        // Only key methods with known semantics may have their invocation count reduced.
        return type == String.class || type == Integer.class || type == Long.class
                || type == Short.class || type == Byte.class || type == Character.class
                || type == Boolean.class || type == Float.class || type == Double.class
                || key instanceof Enum<?> || IDENTITY_KEYS.get(type);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public static int sum(Object2IntOpenHashMap<?> map, Object key, int delta) {
        // 8.5.9's concrete merge does one find(), compares query.equals(stored),
        // handles every defaultReturnValue, and returns the new value. addTo()
        // reverses equals direction and returns the old value, so is not used here.
        return ((Object2IntOpenHashMap<Object>) map).merge(key, delta, SUM);
    }
}
