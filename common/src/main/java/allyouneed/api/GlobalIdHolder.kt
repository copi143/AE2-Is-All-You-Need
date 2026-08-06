package allyouneed.api

/**
 * Mixed into [appeng.api.stacks.AEKey] to expose a process-global, NBT-excluded
 * integer identity for fast search/dedup. The id is assigned lazily on first read
 * and cached on the instance.
 *
 * 注入至 [appeng.api.stacks.AEKey]，暴露去 NBT 的进程全局整数 ID，
 * 用于快速搜索与去重。ID 在首次读取时惰性分配并缓存于实例。
 */
interface GlobalIdHolder {
    /** Stable per-session id (NBT-excluded); -1 means not yet assigned. */
    val globalId: Int

    /** 使缓存失效 */
    fun invalidateGlobalId()
}
