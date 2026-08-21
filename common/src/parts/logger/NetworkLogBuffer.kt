package allyouneed.parts.logger

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import java.util.ArrayDeque

class NetworkLogBuffer(val loggerId: Int) {
    val entries = ArrayDeque<NetworkLogEntry>(LogStore.MAX_ENTRIES)
    var dirty: Boolean = false

    fun append(entry: NetworkLogEntry) {
        if (entries.size >= LogStore.MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(entry)
        dirty = true
    }

    fun clear() {
        if (entries.isNotEmpty()) {
            entries.clear()
            dirty = true
        }
    }

    fun query(offset: Int, filter: Int, limit: Int): NetworkLogPage {
        val filtered = if (filter == NetworkLogCategory.ALL) {
            entries.toList()
        } else {
            entries.filter { it.kind.category.mask and filter != 0 }
        }
        val total = filtered.size
        val from = offset.coerceIn(0, total)
        val to = (from + limit).coerceAtMost(total)
        return NetworkLogPage(ArrayList(filtered.subList(from, to)), total, from)
    }

    fun toNbt(): CompoundTag {
        val root = CompoundTag()
        root.putInt("v", 1)
        val list = ListTag()
        for (entry in entries) {
            list.add(entry.toNbt())
        }
        root.put("e", list)
        return root
    }

    companion object {
        fun fromNbt(loggerId: Int, tag: CompoundTag): NetworkLogBuffer {
            val data = NetworkLogBuffer(loggerId)
            val list = tag.getList("e", Tag.TAG_COMPOUND.toInt())
            for (i in list.indices) {
                data.entries.addLast(NetworkLogEntry.fromNbt(list.getCompound(i)))
            }
            while (data.entries.size > LogStore.MAX_ENTRIES) {
                data.entries.removeFirst()
            }
            data.dirty = false
            return data
        }
    }
}
