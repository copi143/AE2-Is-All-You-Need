package net.minecraft.nbt

open class Tag {
    companion object {
        const val TAG_END: Byte = 0
        const val TAG_BYTE: Byte = 1
        const val TAG_SHORT: Byte = 2
        const val TAG_INT: Byte = 3
        const val TAG_LONG: Byte = 4
        const val TAG_FLOAT: Byte = 5
        const val TAG_DOUBLE: Byte = 6
        const val TAG_BYTE_ARRAY: Byte = 7
        const val TAG_STRING: Byte = 8
        const val TAG_LIST: Byte = 9
        const val TAG_COMPOUND: Byte = 10
        const val TAG_INT_ARRAY: Byte = 11
        const val TAG_LONG_ARRAY: Byte = 12
    }
}
