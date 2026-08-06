package allyouneed.util.id

import appeng.api.stacks.AEKey
import appeng.api.stacks.AEKeyType
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-global, NBT-excluded integer identity for [AEKey]s.
 *
 * Two AEKeys that share the same [AEKeyType] and [AEKey.getPrimaryKey]
 * (i.e. the same item/fluid regardless of NBT) resolve to the SAME id.
 *
 * 进程全局、去 NBT 的 AEKey 整数身份。
 *
 * 共享同一样品/流体（忽略 NBT）的 AEKey 获得同一个 ID。
 */
object KeyIdRegistry {
    private val ids: ConcurrentHashMap<AEKeyType, Object2IntOpenHashMap<Any>> = ConcurrentHashMap()
    private val keys = ArrayList<AEKey>(1024)

    @JvmStatic
    fun assign(key: AEKey): Int {
        val type = ids.computeIfAbsent(key.type) { Object2IntOpenHashMap(128) }
        return synchronized(type) {
            type.computeIfAbsent(key.primaryKey) {
                keys.add(key.dropSecondary())
                keys.size - 1
            }
        }
    }

    @JvmStatic
    operator fun get(id: Int): AEKey? = if (id < 0) null else keys[id]

    @JvmStatic
    operator fun get(key: AEKey): Int = ids[key.type]?.getOrDefault(key.primaryKey, -1) ?: -1

    @JvmStatic
    fun contains(id: Int): Boolean = id in keys.indices

    @JvmStatic
    fun contains(key: AEKey): Boolean = ids[key.type]?.contains(key.primaryKey) ?: false

    @JvmStatic
    fun size(): Int = keys.size

    @JvmStatic
    fun clear() {
        // 似乎不应该这么做
//        byType.clear()
//        reverse.clear()
//        nextId.set(FIRST_ID)
    }
}
