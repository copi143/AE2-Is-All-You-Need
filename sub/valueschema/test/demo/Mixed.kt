package io.github.copi143.valueschema.demo

import io.github.copi143.valueschema.ValueSchema

@ValueSchema
data class Mixed(
    val l: Long,
    val i: Int,
    val d: Double,
    val f: Float,
    val s: Short,
    val b: Byte,
    val bool: Boolean,
    val c: Char,
)
