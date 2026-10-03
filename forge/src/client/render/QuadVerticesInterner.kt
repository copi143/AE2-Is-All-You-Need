package allyouneed.client.render

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 烘焙顶点数组的内容去重池。
 *
 * 供变换后的 GTCEu 类调用，必须位于 mixin 包之外；[intern] 通过 [JvmStatic]
 * 提供公开静态入口，Java 调用保持为 `QuadVerticesInterner.intern(vertices)`。
 *
 * GTCEu 的机器模型会反复 bake，产生大量内容完全相同的 `int[32]` 顶点数组
 * （实测两组各 9559 份完全一致）。烘焙结果被当作不可变数据使用：
 * `GTQuadTransformers.copy/setColor` 和 `FacadeCoverRenderer` 在修改前会复制，
 * 因此相同内容可以安全共享。
 *
 * key 和 value 都使用弱引用；没有 quad 引用某个数组后，条目会在定期清理时移除。
 * `-Dallyouneed.quadDedup.disable=true` 可关闭数组去重（保留 textureKey intern）。
 */
object QuadVerticesInterner {
    private val enabled = !java.lang.Boolean.getBoolean("allyouneed.quadDedup.disable")
    private val pool = ConcurrentHashMap<Key, WeakReference<IntArray>>()

    /** 每 4096 次 intern 做一次 O(n) 清理。 */
    private const val PURGE_MASK = 0xFFF
    private var calls = 0

    @JvmStatic
    fun intern(vertices: IntArray?): IntArray? {
        if (!enabled || vertices == null) return vertices
        if (++calls and PURGE_MASK == 0) purge()

        // probe 只在本次调用存活，不会进入 pool。
        pool[Key(vertices)]?.get()?.let { return it }
        val previous = pool.putIfAbsent(Key(vertices), WeakReference(vertices))
        return previous?.get() ?: vertices
    }

    private fun purge() {
        pool.entries.removeIf { it.key.get() == null || it.value.get() == null }
    }

    /** 数组内容在烘焙后保持不变，因此可以在构造时计算并缓存内容哈希。 */
    private class Key(referent: IntArray) : WeakReference<IntArray>(referent) {
        private val hash = referent.contentHashCode()

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Key) return false
            val a = get() ?: return false
            val b = other.get() ?: return false
            return a.contentEquals(b)
        }
    }
}
