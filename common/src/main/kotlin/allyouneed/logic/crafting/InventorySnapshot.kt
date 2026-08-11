package allyouneed.logic.crafting

import allyouneed.api.BigStackSource
import allyouneed.mixin.ae2.FuzzySearchAccessor
import allyouneed.util.bigint.BigKeyCounter
import allyouneed.util.bigint.BigStack
import appeng.api.config.FuzzyMode
import appeng.api.networking.IGrid
import appeng.api.stacks.AEKey

class InventorySnapshot(grid: IGrid) {
    val stored = BigKeyCounter().apply {
        if (!BigStackSource.collectBigStacks(grid.storageService.inventory, this)) {
            this.addAll(grid.storageService.cachedInventory)
        }
    }
    val storedSet = stored.keys
    val craftable = grid.craftingService.getCraftables { true }!!
    val fuzzyStored = stored.entries.groupBy { it.key.dropSecondary() }.mapValues {
        it.value.associate { (key, value) -> Pair(key, value) }.toSortedMap(comparator)
    }
    val fuzzyCraftable = craftable.groupBy { it.dropSecondary() }.mapValues {
        it.value.associateWith { 0L }.toSortedMap(comparator)
    }

    operator fun contains(key: AEKey): Boolean = storedSet.contains(key) || craftable.contains(key)

    operator fun contains(fuzzy: Pair<AEKey, FuzzyMode>): Boolean {
        val map1 = fuzzyStored[fuzzy.first.dropSecondary()]
        if (map1 != null) {
            FuzzySearchAccessor.invokeFindFuzzy(map1, fuzzy.first, fuzzy.second).isNotEmpty() && return true
        }
        val map2 = fuzzyCraftable[fuzzy.first.dropSecondary()]
        if (map2 != null) {
            FuzzySearchAccessor.invokeFindFuzzy(map2, fuzzy.first, fuzzy.second).isNotEmpty() && return true
        }
        return false
    }

    fun fuzzy(key: AEKey, fuzzy: FuzzyMode): List<BigStack> {
        val list = ArrayList<BigStack>()
        val map1 = fuzzyStored[key.dropSecondary()]
        if (map1 != null) {
            FuzzySearchAccessor.invokeFindFuzzy(map1, key, fuzzy).forEach {
                list.add(BigStack(it.key, it.value))
            }
        }
        val map2 = fuzzyCraftable[key.dropSecondary()]
        if (map2 != null) {
            FuzzySearchAccessor.invokeFindFuzzy(map2, key, fuzzy).forEach {
                list.add(BigStack(it.key, it.value))
            }
        }
        return list
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        val comparator = (Class.forName("appeng.api.stacks.FuzzySearch") //
            .getDeclaredField("COMPARATOR") //
            .get(null) as? Comparator<Any>)!!
    }
}
