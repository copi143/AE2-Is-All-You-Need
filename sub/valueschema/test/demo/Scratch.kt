package io.github.copi143.valueschema.demo

import io.github.copi143.valueschema.Default
import io.github.copi143.valueschema.ValueSchema

@ValueSchema
data class Scratch(
    val value: Int,
    @Default("-1") val link: Int,
    @Default("true") val active: Boolean,
    val flag: Boolean,
)
