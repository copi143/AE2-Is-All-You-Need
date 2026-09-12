package allyouneed.util

import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

fun <T, R : Tag> ListTag.addMapped(list: List<T>, transform: (T) -> R): ListTag {
    for (e in list) {
        add(transform(e))
    }
    return this
}
