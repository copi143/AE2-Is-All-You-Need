package allyouneed.parts.logger

import io.github.copi143.valueschema.ValueSchema
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

@ValueSchema
data class NetworkLogRow(val time: Long, val kind: NetworkLogKind)

/**
 * Bounded ring of the last [LogStore.MAX_ENTRIES] log entries. Rows live in generated columns
 * (time/kind-ordinal) so category-mask filter scans stay on flat primitive arrays; variable-length
 * args stay in a parallel array until the generator grows CSR support.
 */
class NetworkLogBuffer(val loggerId: Int) {
    private val rows = NetworkLogRowColumns(LogStore.MAX_ENTRIES).apply { resize(LogStore.MAX_ENTRIES) }
    private val args = arrayOfNulls<List<String>>(LogStore.MAX_ENTRIES)
    private var head = 0
    var size = 0
        private set
    var dirty: Boolean = false

    fun append(entry: NetworkLogEntry) {
        val idx = if (size < LogStore.MAX_ENTRIES) {
            val i = (head + size) % LogStore.MAX_ENTRIES
            size++
            i
        } else {
            val i = head
            head = (head + 1) % LogStore.MAX_ENTRIES
            i
        }
        rows.set(idx, entry.utcMillis, entry.kind)
        args[idx] = entry.args
        dirty = true
    }

    fun clear() {
        if (size > 0) {
            head = 0
            size = 0
            args.fill(null)
            dirty = true
        }
    }

    fun entryAt(i: Int): NetworkLogEntry {
        val idx = (head + i) % LogStore.MAX_ENTRIES
        return NetworkLogEntry(rows.times[idx], NetworkLogKind.byOrdinal(rows.kinds[idx]), args[idx] ?: emptyList())
    }

    fun count(filter: Int): Int {
        if (filter == NetworkLogCategory.All) return size
        var n = 0
        for (i in 0 until size) {
            if (KIND_CATEGORY_MASKS[rows.kinds[(head + i) % LogStore.MAX_ENTRIES]] and filter != 0) n++
        }
        return n
    }

    fun all(): List<NetworkLogEntry> = List(size) { entryAt(it) }

    fun query(offset: Int, filter: Int, limit: Int): NetworkLogPage {
        val page = ArrayList<NetworkLogEntry>(minOf(limit, size))
        val from = offset.coerceAtLeast(0)
        var total = 0
        for (i in 0 until size) {
            val idx = (head + i) % LogStore.MAX_ENTRIES
            if (KIND_CATEGORY_MASKS[rows.kinds[idx]] and filter != 0) {
                if (total >= from && page.size < limit) page.add(entryAt(i))
                total++
            }
        }
        return NetworkLogPage(page, total, from.coerceAtMost(total))
    }

    fun toNbt(): CompoundTag {
        val root = CompoundTag()
        root.putInt("v", 1)
        val list = ListTag()
        for (i in 0 until size) {
            list.add(entryAt(i).toNbt())
        }
        root.put("e", list)
        return root
    }

    companion object {
        fun fromNbt(loggerId: Int, tag: CompoundTag): NetworkLogBuffer {
            val data = NetworkLogBuffer(loggerId)
            val list = tag.getList("e", Tag.TAG_COMPOUND.toInt())
            for (i in list.indices) {
                data.append(NetworkLogEntry.fromNbt(list.getCompound(i)))
            }
            data.dirty = false
            return data
        }
    }
}
