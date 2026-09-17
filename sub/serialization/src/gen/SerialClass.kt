package io.github.copi143.serialization.gen

data class SerialClass(
    val pkg: String,
    val name: String,
    val fields: List<SerialProp>,
    val construct: Boolean,
)
