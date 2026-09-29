@file:Suppress("NOTHING_TO_INLINE")

package allyouneed.util

import net.minecraft.nbt.*

inline val Byte.nbt: ByteTag get() = ByteTag.valueOf(this)
inline val UByte.nbt: ByteTag get() = ByteTag.valueOf(this.toByte())
inline val Short.nbt: ShortTag get() = ShortTag.valueOf(this)
inline val UShort.nbt: ShortTag get() = ShortTag.valueOf(this.toShort())
inline val Int.nbt: IntTag get() = IntTag.valueOf(this)
inline val UInt.nbt: IntTag get() = IntTag.valueOf(this.toInt())
inline val Long.nbt: LongTag get() = LongTag.valueOf(this)
inline val ULong.nbt: LongTag get() = LongTag.valueOf(this.toLong())
inline val Float.nbt: FloatTag get() = FloatTag.valueOf(this)
inline val Double.nbt: DoubleTag get() = DoubleTag.valueOf(this)
inline val String.nbt: StringTag get() = StringTag.valueOf(this)

inline fun <T, R : Tag> ListTag.addMapped(list: List<T>, transform: (T) -> R): ListTag {
    for (e in list) {
        add(transform(e))
    }
    return this
}

inline fun <T, R : Tag> CompoundTag.addMapped(map: Map<String, T>, transform: (T) -> R): CompoundTag {
    for ((k, v) in map) {
        put(k, transform(v))
    }
    return this
}

inline fun List<String>.toListTag(): ListTag {
    return ListTag().addMapped(this) { it.nbt }
}

inline fun Map<String, String>.toCompoundTag(): CompoundTag {
    return CompoundTag().addMapped(this) { it.nbt }
}
