package averith.resource

import averith.util.GlobalID
import averith.util.StringRepresentable

/**
 * 比 ResourceType 更小的一级。
 */
class ResourceKind private constructor(val type: ResourceType, val id: GlobalID): StringRepresentable {
    val category: ResourceCategory get() = type.category

    override val string: String = "<ResourceKind `${category.id}:${type.id}:$id`>"

    override fun toString(): String = string
}
