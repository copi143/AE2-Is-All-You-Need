package net.minecraft.nbt

class ListTag : Tag() {
    private val list = mutableListOf<Tag>()
    val size: Int get() = list.size
    fun add(tag: Tag) { list.add(tag) }
    operator fun get(i: Int): Tag = list[i]
    fun getString(i: Int): String = (list[i] as? StringTag)?.getAsString() ?: ""
    fun getCompound(i: Int): CompoundTag = list[i] as? CompoundTag ?: CompoundTag()
    fun addAll(other: ListTag) { list.addAll(other.list) }
    fun toList(): List<Tag> = list
    val indices: IntRange get() = list.indices
    operator fun iterator(): Iterator<Tag> = list.iterator()
    override fun equals(other: Any?): Boolean { if (other !is ListTag) return false; if (list.size != other.list.size) return false; for (i in list.indices) if (list[i] != other.list[i]) return false; return true }
    override fun hashCode(): Int = list.hashCode()
}
