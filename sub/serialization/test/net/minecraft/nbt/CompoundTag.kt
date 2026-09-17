package net.minecraft.nbt

import java.util.UUID

class CompoundTag : Tag() {
    private val map = mutableMapOf<String, Any?>()
    private val types = mutableMapOf<String, Byte>()

    fun putByte(key: String, v: Byte) { map[key] = v; types[key] = Tag.TAG_BYTE }
    fun getByte(key: String): Byte = map[key] as? Byte ?: 0
    fun putShort(key: String, v: Short) { map[key] = v; types[key] = Tag.TAG_SHORT }
    fun getShort(key: String): Short = map[key] as? Short ?: 0
    fun putInt(key: String, v: Int) { map[key] = v; types[key] = Tag.TAG_INT }
    fun getInt(key: String): Int = map[key] as? Int ?: 0
    fun putLong(key: String, v: Long) { map[key] = v; types[key] = Tag.TAG_LONG }
    fun getLong(key: String): Long = map[key] as? Long ?: 0L
    fun putFloat(key: String, v: Float) { map[key] = v; types[key] = Tag.TAG_FLOAT }
    fun getFloat(key: String): Float = map[key] as? Float ?: 0f
    fun putDouble(key: String, v: Double) { map[key] = v; types[key] = Tag.TAG_DOUBLE }
    fun getDouble(key: String): Double = map[key] as? Double ?: 0.0
    fun putString(key: String, v: String) { map[key] = v; types[key] = Tag.TAG_STRING }
    fun getString(key: String): String = map[key] as? String ?: ""
    fun putBoolean(key: String, v: Boolean) { map[key] = v; types[key] = Tag.TAG_BYTE }
    fun getBoolean(key: String): Boolean = map[key] as? Boolean ?: false
    fun putByteArray(key: String, v: ByteArray) { map[key] = v.clone(); types[key] = Tag.TAG_BYTE_ARRAY }
    fun getByteArray(key: String): ByteArray = (map[key] as? ByteArray)?.clone() ?: ByteArray(0)
    fun putIntArray(key: String, v: IntArray) { map[key] = v.clone(); types[key] = Tag.TAG_INT_ARRAY }
    fun getIntArray(key: String): IntArray = (map[key] as? IntArray)?.clone() ?: IntArray(0)
    fun putLongArray(key: String, v: LongArray) { map[key] = v.clone(); types[key] = Tag.TAG_LONG_ARRAY }
    fun getLongArray(key: String): LongArray = (map[key] as? LongArray)?.clone() ?: LongArray(0)
    fun putUUID(key: String, v: UUID) { map[key] = v; types[key] = Tag.TAG_INT_ARRAY }
    fun getUUID(key: String): UUID = map[key] as? UUID ?: UUID(0L, 0L)
    fun put(key: String, v: Tag) {
        map[key] = v
        types[key] = when (v) {
            is CompoundTag -> Tag.TAG_COMPOUND
            is ListTag -> Tag.TAG_LIST
            else -> Tag.TAG_END
        }
    }
    fun getCompound(key: String): CompoundTag = map[key] as? CompoundTag ?: CompoundTag()
    fun getList(key: String, type: Int): ListTag = map[key] as? ListTag ?: ListTag()
    fun contains(key: String): Boolean = map.containsKey(key)
    fun getTagType(key: String): Byte = types[key] ?: Tag.TAG_END
    val allKeys: Set<String> get() = map.keys
    fun copy(): CompoundTag { val c = CompoundTag(); c.map.putAll(map); c.types.putAll(types); return c }
    override fun equals(other: Any?): Boolean {
        if (other !is CompoundTag) return false
        if (map.size != other.map.size) return false
        for ((k, v) in map) {
            val ov = other.map[k] ?: return false
            if (v is ByteArray && ov is ByteArray) { if (!v.contentEquals(ov)) return false }
            else if (v is IntArray && ov is IntArray) { if (!v.contentEquals(ov)) return false }
            else if (v is LongArray && ov is LongArray) { if (!v.contentEquals(ov)) return false }
            else if (v != ov) return false
        }
        return true
    }
    override fun hashCode(): Int = map.hashCode()
    override fun toString(): String = map.toString()
}
