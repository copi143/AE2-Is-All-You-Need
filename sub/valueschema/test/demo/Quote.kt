package io.github.copi143.valueschema.demo

import io.github.copi143.valueschema.ValueSchema

@ValueSchema
data class Quote(val id: Long, val price: Int, val ts: Long)
