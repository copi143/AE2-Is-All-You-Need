package net.minecraft.nbt

class StringTag private constructor(private val v: String) : Tag() {
    fun getAsString(): String = v
    override fun equals(other: Any?): Boolean = other is StringTag && v == other.v
    override fun hashCode(): Int = v.hashCode()
    companion object {
        fun valueOf(s: String): StringTag = StringTag(s)
    }
}
