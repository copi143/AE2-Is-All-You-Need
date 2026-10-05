package perf;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public final class MapAccumulationWorkload implements AccumulationWorkload {
    private final Object[] input = new Object[16384];

    public static final class CustomKey {
        private final int id;
        public CustomKey(int id) { this.id = id; }
        @Override public int hashCode() { return id; }
    }

    public MapAccumulationWorkload(String kind, int distinct) {
        Object[] keys = new Object[distinct];
        for (int i = 0; i < distinct; i++) keys[i] = switch (kind) {
            case "identity" -> new Object();
            case "string" -> "ingredient-" + i;
            case "custom" -> new CustomKey(i);
            default -> throw new IllegalArgumentException(kind);
        };
        for (int i = 0; i < input.length; i++) input[i] = keys[(i * 37 + 17) & (distinct - 1)];
    }

    @Override
    public Object count() {
        Object2IntOpenHashMap<Object> result = new Object2IntOpenHashMap<>();
        for (Object key : input) result.mergeInt(key, 1, Integer::sum);
        return result;
    }

    @Override
    public int checksum(Object result) {
        int sum = 0;
        for (int value : ((Object2IntOpenHashMap<?>) result).values()) sum += value;
        return sum;
    }
}
