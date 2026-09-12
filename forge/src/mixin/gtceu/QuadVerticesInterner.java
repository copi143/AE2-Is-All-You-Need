package allyouneed.mixin.gtceu;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 烘焙顶点数组的内容去重池。
 *
 * <p>GTCEu 的机器模型会反复 bake，产生大量内容完全相同的 {@code int[32]} 顶点数组
 * （实测两组各 9559 份完全一致）。烘焙结果一旦返回就被当作不可变数据使用
 * （调用方要原地改都会先 {@code clone()}，见 {@code GTQuadTransformers.copy/setColor}
 * 与 FacadeCoverRenderer 里 "copy the quad so we don't mutate the original" 的注释），
 * 因此内容相同的数组实例可以安全共享。
 *
 * <p>key 和 value 都是弱引用：没有任何 quad 再引用某个内容时，对应条目在下次
 * 定期 purge 时被清除，不会因为资源重载反复烘焙而无限增长。用
 * {@code -Dallyouneed.quadDedup.disable=true} 可整体关闭去重（只保留 textureKey intern）。
 */
final class QuadVerticesInterner {
    static final boolean ENABLED = !Boolean.getBoolean("allyouneed.quadDedup.disable");

    private static final ConcurrentHashMap<Key, WeakReference<int[]>> POOL = new ConcurrentHashMap<>();

    /** 每 4096 次 intern 做一次 O(n) 清理，均摊可忽略。 */
    private static final int PURGE_MASK = 0xFFF;
    private static int calls;

    private QuadVerticesInterner() {
    }

    static int[] intern(int[] vertices) {
        if (!ENABLED || vertices == null) {
            return vertices;
        }
        if ((++calls & PURGE_MASK) == 0) {
            purge();
        }
        // probe 只在本次调用存活，不会进 POOL。
        WeakReference<int[]> ref = POOL.get(new Key(vertices));
        int[] cached = ref == null ? null : ref.get();
        if (cached != null) {
            return cached;
        }
        WeakReference<int[]> fresh = new WeakReference<>(vertices);
        WeakReference<int[]> prev = POOL.putIfAbsent(new Key(vertices), fresh);
        if (prev != null) {
            int[] raced = prev.get();
            if (raced != null) {
                return raced;
            }
        }
        return vertices;
    }

    private static void purge() {
        POOL.entrySet().removeIf(e -> e.getKey().get() == null || e.getValue().get() == null);
    }

    /**
     * 按数组内容比较的弱 key。hash 在构造时按当时内容预计算——烘焙返回后的
     * 顶点数组内容不再变化（见类注释），因此 hash 稳定。
     */
    private static final class Key extends WeakReference<int[]> {
        private final int hash;

        Key(int[] referent) {
            super(referent);
            this.hash = Arrays.hashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            int[] a = get();
            int[] b = other.get();
            return a != null && b != null && Arrays.equals(a, b);
        }
    }
}
