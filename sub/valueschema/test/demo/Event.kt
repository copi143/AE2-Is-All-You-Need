package io.github.copi143.valueschema.demo

import io.github.copi143.valueschema.Default
import io.github.copi143.valueschema.ValueSchema
import io.github.copi143.valueschema.ValueTransform

enum class EventKind { Created, Updated, Deleted }

@ValueSchema(
    transforms = [
        ValueTransform(
            name = "markDeleted",
            body = "kind = EventKind.Deleted",
        ),
    ],
)
data class Event(
    val id: Long,
    @Default("Updated") val kind: EventKind,
    val flag: Boolean,
)
