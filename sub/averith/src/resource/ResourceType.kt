package averith.resource

import averith.util.GlobalID
import averith.util.StringRepresentable

/**
 * 比 [ResourceCategory] 更小一级的分类，类似：
 * - 石头
 * - 木头
 * - 水
 */
class ResourceType private constructor(val category: ResourceCategory, val id: GlobalID): StringRepresentable {
    override val string: String = "<ResourceType `${category.id}:$id`>"

    override fun toString(): String = string

    companion object {
        private val registry: MutableMap<Pair<ResourceCategory, GlobalID>, ResourceType> = mutableMapOf()

        @JvmStatic
        fun register(category: ResourceCategory, id: GlobalID): ResourceType {
            return registry.getOrPut(Pair(category, id)) { ResourceType(category, id) }
        }

        @JvmStatic
        fun register(category: ResourceCategory, id: String) :ResourceType {
            return register(category, GlobalID.register(id))
        }
    }
}
