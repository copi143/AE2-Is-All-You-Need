package averith.resource

import averith.util.GlobalID
import averith.util.StringRepresentable

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
