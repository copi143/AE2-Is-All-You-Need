package allyouneed.util.id

import allyouneed.api.KeyIdHolder
import appeng.api.stacks.AEKey

/**
 * Process-global integer identities for [AEKey]s.
 *
 * - [assignPrimary]: same [AEKey.getType] + [AEKey.getPrimaryKey] (NBT ignored) share an id.
 * - [assignSecondary]: `(primaryId << 32) | local`. Content-equal keys share [local].
 *   If [AEKey.dropSecondary] equals the key, [local] is 0.
 *
 * Entries are held via weak references so unused keys are collected and their ids recycled.
 * The registry never strongly retains an [AEKey].
 *
 * 进程全局 AEKey 整数身份。
 *
 * - primary：同类型 + 同 primaryKey（忽略 NBT）共享 ID。
 * - secondary：`(primaryId << 32) | local`；无 secondary 时为 `primaryId << 32`。
 *
 * 仅弱引用登记过的实例；key 不可达后自动回收 ID，注册表不强持有 AEKey。
 */
object KeyIdRegistry {
    private val primary = WeakInternedIds(::primaryHash, ::primaryEq)
    private val secondary = WeakInternedIds(AEKey::hashCode) { a, b -> a == b }

    @JvmStatic
    fun packSecondaryId(primaryId: Int, local: Int): Long =
        (primaryId.toLong() shl 32) or (local.toLong() and 0xFFFF_FFFFL)

    @JvmStatic
    fun unpackPrimaryId(secondaryId: Long): Int = (secondaryId ushr 32).toInt()

    @JvmStatic
    fun unpackLocalId(secondaryId: Long): Int = secondaryId.toInt()

    @JvmStatic
    fun assignPrimary(key: AEKey): Int = primary.assign(key)

    @JvmStatic
    fun assignSecondary(key: AEKey): Long {
        val pid = assignPrimary(key)
        if (key.dropSecondary() == key) return packSecondaryId(pid, 0)
        return packSecondaryId(pid, secondary.assign(key) + 1)
    }

    @JvmStatic
    fun peekPrimary(key: AEKey): Int = primary.peek(key)

    @JvmStatic
    fun peekSecondary(key: AEKey): Long {
        val pid = peekPrimary(key)
        if (pid < 0) return -1L
        if (key.dropSecondary() == key) return packSecondaryId(pid, 0)
        val local = secondary.peek(key)
        if (local < 0) return -1L
        return packSecondaryId(pid, local + 1)
    }

    @JvmStatic
    fun findPrimary(id: Int): AEKey? = primary.find(id)

    @JvmStatic
    fun findSecondary(id: Long): AEKey? {
        if (id < 0) return null
        val local = unpackLocalId(id)
        if (local == 0) return primary.find(unpackPrimaryId(id))
        return secondary.find(local - 1)
    }

    @JvmStatic
    fun primarySize(): Int = primary.size()

    @JvmStatic
    fun secondarySize(): Int = secondary.size()

    @JvmStatic
    fun clear() {
        primary.clear { (it as? KeyIdHolder)?.invalidateKeyIds() }
        secondary.clear { (it as? KeyIdHolder)?.invalidateKeyIds() }
    }

    private fun primaryHash(key: AEKey): Int {
        val pk = key.primaryKey
        return 31 * System.identityHashCode(key.type) + (pk?.hashCode() ?: 0)
    }

    private fun primaryEq(a: AEKey, b: AEKey): Boolean =
        a.type === b.type && a.primaryKey == b.primaryKey
}
