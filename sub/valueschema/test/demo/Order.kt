package io.github.copi143.valueschema.demo

import io.github.copi143.valueschema.ValueSchema
import io.github.copi143.valueschema.ValueTransform

@ValueSchema
data class Money(val amount: Long, val scale: Int)

@ValueSchema(
    transforms = [
        ValueTransform(
            name = "discount",
            params = "bps: Int",
            body = "price_amount -= price_amount * bps / 10000",
        ),
    ],
)
data class Order(val id: Long, val price: Money, val qty: Int)
