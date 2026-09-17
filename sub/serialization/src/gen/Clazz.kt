package io.github.copi143.serialization.gen

import com.squareup.kotlinpoet.ClassName

object Clazz {
    val Tag = ClassName("net.minecraft.nbt", "Tag")
    val ByteTag = ClassName("net.minecraft.nbt", "ByteTag")
    val ShortTag = ClassName("net.minecraft.nbt", "ShortTag")
    val IntTag = ClassName("net.minecraft.nbt", "IntTag")
    val LongTag = ClassName("net.minecraft.nbt", "LongTag")
    val NumericTag = ClassName("net.minecraft.nbt", "NumericTag")
    val CompoundTag = ClassName("net.minecraft.nbt", "CompoundTag")
    val ListTag = ClassName("net.minecraft.nbt", "ListTag")
    val StringTag = ClassName("net.minecraft.nbt", "StringTag")

    val FriendlyByteBuf = ClassName("net.minecraft.network", "FriendlyByteBuf")
    val ResourceLocation = ClassName("net.minecraft.resources", "ResourceLocation")
    val BlockPos = ClassName("net.minecraft.core", "BlockPos")

    val BigInteger = ClassName("java.math", "BigInteger")
    val UUID = ClassName("java.util", "UUID")
}
