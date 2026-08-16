package allyouneed.api

/**
 * Mixed into [appeng.api.stacks.AEKey] to expose process-global integer identities.
 *
 * - [primaryId]: same type + primary key (NBT ignored).
 * - [secondaryId]: `(primaryId << 32) | local`. `local == 0` iff `dropSecondary()` equals this key.
 *
 * 注入至 [appeng.api.stacks.AEKey] 的进程全局整数身份。
 * [secondaryId] 高 32 位为 [primaryId]；无 secondary 时低 32 位为 0。
 */
interface KeyIdHolder {
    val primaryId: Int
    val secondaryId: Long

    fun invalidateKeyIds()
}
