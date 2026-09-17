package net.minecraft.nbt

abstract class NumericTag : Tag() {
    abstract fun getAsInt(): Int
    open fun getAsLong(): Long = getAsInt().toLong()
}
