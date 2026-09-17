package io.github.copi143.serialization.gen

data class SerialProp(
    val name: String,
    val getter: String,
    val setter: String?,
    val wireName: String,
    val type: SerialType,
    val nullable: Boolean,
    val varLen: Boolean = false,
)
