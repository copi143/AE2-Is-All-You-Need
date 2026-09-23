package io.github.copi143.valueschema.demo

import io.github.copi143.valueschema.ValueSchema
import io.github.copi143.valueschema.ValueTransform

@ValueSchema(
    transforms = [
        ValueTransform(
            name = "cappedAt",
            params = "limit: Int",
            body = "if (price > limit) price = limit",
        ),
        ValueTransform(
            name = "scale",
            params = "factor: Int, delta: Long",
            body = "price *= factor\nts += delta",
        ),
    ],
)
data class Quote(val id: Long, val price: Int, val ts: Long)
