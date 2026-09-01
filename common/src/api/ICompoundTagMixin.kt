package allyouneed.api

import net.minecraft.nbt.Tag

interface ICompoundTagMixin {
    val key1: String?
    val key2: String?
    val key3: String?
    val key4: String?

    val value1: Tag?
    val value2: Tag?
    val value3: Tag?
    val value4: Tag?
}
