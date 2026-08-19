package allyouneed.logic.crafting

import allyouneed.api.BigStackSource
import allyouneed.util.bigint.BigKeyCounter
import appeng.api.config.FuzzyMode
import appeng.api.networking.IGrid
import appeng.api.stacks.AEKey
import java.util.*

class InventorySnapshot(grid: IGrid) {
    val stored = BigKeyCounter().apply {
        if (!BigStackSource.collectBigStacks(grid.storageService.inventory, this)) {
            this.addAll(grid.storageService.cachedInventory)
        }
    }
    val craftable = grid.craftingService.getCraftables { true }!!
    val fuzzyStored = stored.entries.groupBy { it.key.dropSecondary() }.mapValues {
        it.value.associate { (key, value) -> Pair(key, value) }.toSortedMap(comparator)
    }
    val fuzzyCraftable = craftable.groupBy { it.dropSecondary() }.mapValues {
        it.value.associateWith { 0L }.toSortedMap(comparator)
    }

    operator fun contains(key: AEKey): Boolean = stored.contains(key) || craftable.contains(key)

    operator fun contains(fuzzy: Pair<AEKey, FuzzyMode>): Boolean {
        val map1 = fuzzyStored[fuzzy.first.dropSecondary()]
        if (map1 != null) {
            invokeFindFuzzy(map1, fuzzy.first, fuzzy.second).isNotEmpty() && return true
        }
        val map2 = fuzzyCraftable[fuzzy.first.dropSecondary()]
        if (map2 != null) {
            invokeFindFuzzy(map2, fuzzy.first, fuzzy.second).isNotEmpty() && return true
        }
        return false
    }

    fun fuzzy(key: AEKey): Set<AEKey> {
        val set = HashSet<AEKey>()
        val baseKey = key.dropSecondary()
        if (baseKey.fuzzySearchMaxValue > 0) {
            fuzzyStored[baseKey]?.forEach { set.add(it.key) }
            fuzzyCraftable[baseKey]?.forEach { set.add(it.key) }
        } else {
            if (stored.contains(key)) set.add(key)
            if (craftable.contains(key)) set.add(key)
        }
        return set
    }

    fun fuzzy(key: AEKey, fuzzy: FuzzyMode): Set<AEKey> {
        val set = HashSet<AEKey>()
        val baseKey = key.dropSecondary()
        if (baseKey.fuzzySearchMaxValue > 0) {
            val map1 = fuzzyStored[baseKey]
            if (map1 != null) {
                invokeFindFuzzy(map1, key, fuzzy).forEach {
                    set.add(it.key)
                }
            }
            val map2 = fuzzyCraftable[baseKey]
            if (map2 != null) {
                invokeFindFuzzy(map2, key, fuzzy).forEach {
                    set.add(it.key)
                }
            }
        } else {
            if (stored.contains(key)) set.add(key)
            if (craftable.contains(key)) set.add(key)
        }
        return set
    }

    companion object {
        private val findFuzzy = Class.forName("appeng.api.stacks.FuzzySearch")
            .getDeclaredMethod("findFuzzy", SortedMap::class.java, AEKey::class.java, FuzzyMode::class.java)
            .apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        fun <T : SortedMap<K, V>, K, V> invokeFindFuzzy(map: T, key: AEKey, fuzzy: FuzzyMode): T =
            findFuzzy.invoke(null, map, key, fuzzy) as T

        @Suppress("UNCHECKED_CAST")
        val comparator = (Class.forName("appeng.api.stacks.FuzzySearch") //
            .getDeclaredField("COMPARATOR") //
            .apply { isAccessible = true } //
            .get(null) as? Comparator<Any>)!!
    }
}
