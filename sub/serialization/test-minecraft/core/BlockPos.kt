package net.minecraft.core

data class BlockPos(val x: Int, val y: Int, val z: Int) {
    fun asLong(): Long = ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)
    companion object {
        @JvmField
        val ZERO = BlockPos(0, 0, 0)
        fun of(v: Long): BlockPos {
            val x = (v shr 38).toInt()
            val y = (v and 0xFFF).toInt()
            val z = (v shr 12 and 0x3FFFFFF).toInt()
            // sign extend
            return BlockPos(x, y, z)
        }
    }
}
