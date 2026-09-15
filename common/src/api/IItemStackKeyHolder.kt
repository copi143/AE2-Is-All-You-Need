package allyouneed.api

import appeng.api.stacks.AEItemKey

interface IItemStackKeyHolder {
    var cachedItemKey: AEItemKey?
    fun invalidateCachedItemKey()
}
