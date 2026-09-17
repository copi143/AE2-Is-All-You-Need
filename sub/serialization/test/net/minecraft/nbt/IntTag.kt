package net.minecraft.nbt

class IntTag private constructor(private val v: Int) : NumericTag() {
    override fun getAsInt(): Int = v
    override fun getAsLong(): Long = v.toLong()
    override fun equals(other: Any?): Boolean = other is IntTag && v == other.v
    override fun hashCode(): Int = v.hashCode()
    companion object {
        fun valueOf(v: Int): IntTag = IntTag(v)
    }
}
