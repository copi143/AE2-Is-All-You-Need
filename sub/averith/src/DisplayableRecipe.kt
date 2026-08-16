package averith

class DisplayableRecipe {
    class MaterialGroup
    class Resource

    class Ingredient
}

/**
 * 资源的最大分类，类似：
 * - 物品
 * - 液体
 * - 气体
 * - 电力
 * - 魔力
 */
class ResourceCategory private constructor(val id: GlobalID): StringRepresentable {
    override val string: String = "<ResourceCategory `$id`>"

    companion object {
        private val registry: MutableMap<GlobalID, ResourceCategory> = mutableMapOf()

        @JvmStatic
        fun register(id: GlobalID): ResourceCategory {
            return registry.getOrPut(id) { ResourceCategory(id) }
        }

        @JvmStatic
        fun register(id: String) :ResourceCategory {
            return register(GlobalID.register(id))
        }
    }
}

class Resource {

}

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

class ResourceKind private constructor(val type: ResourceType, val id: GlobalID): StringRepresentable {
    val category: ResourceCategory get() = type.category

    override val string: String = "<ResourceKind `${category.id}:${type.id}:$id`>"

    override fun toString(): String = string
}
