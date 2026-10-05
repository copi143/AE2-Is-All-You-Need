package mapaccumulation.fixtures;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;

public class AccumulationCalls {
    public static int direct(Object2IntOpenHashMap<Object> map, Object key, int delta) {
        return map.mergeInt(key, delta, Integer::sum);
    }

    public static int throughInterface(Object2IntMap<Object> map, Object key, int delta) {
        return map.mergeInt(key, delta, Integer::sum);
    }

    public static int localAlias(Object2IntMap<Object> map, Object key, int delta) {
        IntBinaryOperator sum = Integer::sum;
        IntBinaryOperator alias = sum;
        return map.mergeInt(key, delta, alias);
    }

    public static int boxed(Object2IntMap<Object> map, Object key, int delta) {
        BiFunction<Integer, Integer, Integer> sum = Integer::sum;
        return map.mergeInt(key, delta, sum);
    }

    public static int knownBranch(Object2IntMap<Object> map, Object key, int delta, boolean flag) {
        IntBinaryOperator sum = flag ? Integer::sum : Integer::sum;
        return map.mergeInt(key, delta, sum);
    }

    public static int unknownBranch(Object2IntMap<Object> map, Object key, int delta, boolean flag, IntBinaryOperator other) {
        IntBinaryOperator op = flag ? Integer::sum : other;
        return map.mergeInt(key, delta, op);
    }

    public static int captured(Object2IntMap<Object> map, Object key, int delta, int extra) {
        return map.mergeInt(key, delta, (a, b) -> a + b + extra);
    }

    public static int maximum(Object2IntMap<Object> map, Object key, int delta) {
        return map.mergeInt(key, delta, Math::max);
    }

    public static int missingCallback(Object2IntMap<Object> map, Object key, int delta) {
        return map.mergeInt(key, delta, (IntBinaryOperator) null);
    }

    public static long stackPrefix(Object2IntMap<Object> map, Object key, int delta) {
        return 1234567890123L + map.mergeInt(key, delta, Integer::sum);
    }

    public static int caught(Object2IntMap<Object> map, Object key, int delta) {
        try {
            return map.mergeInt(key, delta, Integer::sum);
        } catch (IllegalStateException | NullPointerException exception) {
            return -23;
        }
    }

    public static String trace;
    private static Object2IntOpenHashMap<Object> receiver(Object2IntOpenHashMap<Object> map) { trace += "r"; return map; }
    private static Object key(Object key) { trace += "k"; return key; }
    private static int delta(int delta) { trace += "d"; return delta; }
    public static int evaluation(Object2IntOpenHashMap<Object> map, Object key, int delta) {
        trace = "";
        return receiver(map).mergeInt(key(key), delta(delta), Integer::sum);
    }
}
