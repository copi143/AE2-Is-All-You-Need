package allyouneed.logic.machine

import allyouneed.util.logger
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * 分子装配室可执行的**配方类别**（不是具体机器方块）。
 * 构造时自动注册；数据包重载会使旧实例 [valid]=false，防止外部持有引用导致幽灵行为。
 *
 * A **recipe category** the machine assembler can execute — not a concrete machine block.
 * Auto-registers on construct; datapack reload sets old instances [valid]=false to avoid ghost use.
 *
 * 解析顺序 / Recipe resolution order:
 * 1. 本 [id] 的数据包手动配方 / Manual datapack recipes ([ManualMachineRecipes])
 * 2. 可选的代码/数据包配方源 / Optional [recipeSource]
 */
class MachineType(
    val id: String,
    val name: Component,
    val icon: ItemStack,
    /**
     * 占用装配室的槽位数。
     * How many slots this category uses.
     */
    val inputSlots: Int,
    val machineMatcher: MachineItemMatcher,
    /**
     * 手动配方未命中时的回退配方源。
     * Fallback backend after manual recipes.
     */
    val recipeSource: MachineRecipeSource? = null,
    /**
     * 可选关联的原版 [RecipeType]；纯手动类型可为 null。
     *
     * Optional linked vanilla [RecipeType]; pure manual types may leave null.
     */
    val recipeType: RecipeType<*>? = null,
    val fromDatapack: Boolean = false,
) {
    /**
     * 是否仍为当前生效实例。重载被替换后为 false。
     *
     * Whether this instance is still the live one. False after reload replacement.
     */
    @Volatile
    var valid: Boolean = false
        private set

    fun validOrNull(): MachineType? = if (valid) this else null

    /**
     * 运行时全局 int16 id。非存档稳定；重载保留 string→id，不回收。
     *
     * Runtime global int16 id. Not save-stable; string→id kept across reloads.
     */
    var networkId: Short = -1
        private set

    init {
        install(this)
    }

    fun accepts(stack: ItemStack): Boolean = valid && machineMatcher.matches(stack)

    /**
     * 根据已填充的装配网格解析产物。
     * Resolve output for the filled assembler grid.
     *
     * 手动数据包配方优先，其次 [recipeSource]。
     * Manual datapack recipes win; then [recipeSource].
     */
    fun resolve(level: Level, container: Container): ItemStack? {
        if (!valid) return null
        ManualMachineRecipes.resolve(this, container)?.let { return it }
        return recipeSource?.resolve(level, container)
    }

    fun remainders(level: Level, container: Container): List<ItemStack> {
        if (!valid) return List(container.containerSize) { ItemStack.EMPTY }
        ManualMachineRecipes.remainders(this, container)?.let { return it }
        return recipeSource?.remainders(level, container) ?: List(container.containerSize) { ItemStack.EMPTY }
    }

    companion object {
        val types = ArrayList<MachineType?>()

        // 代码侧常驻（重载后可恢复为 live）
        private val codeById = LinkedHashMap<String, MachineType>()

        // 当前生效实例（byId / getAll）
        private val liveById = LinkedHashMap<String, MachineType>()

        // networkId：进程内不删除
        private val stringToNetworkId = HashMap<String, Short>()

        fun idOf(recipeType: RecipeType<*>): String {
            val key = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType)
            return key?.toString() ?: recipeType.toString()
        }

        fun idOf(rl: ResourceLocation): String = rl.toString()

        /**
         * 数据包重载开始：使所有数据包 live 实例失效，并恢复未被覆盖的代码类型。
         *
         * Begin datapack reload: invalidate live datapack instances, restore code types.
         */
        fun beginDatapackReload() {
            val datapackIds = liveById.filter { it.value.fromDatapack }.keys.toList()
            for (id in datapackIds) {
                val old = liveById.remove(id) ?: continue
                old.valid = false
                val code = codeById[id]
                if (code != null) {
                    code.valid = true
                    liveById[id] = code
                }
            }
        }

        /**
         * 数据包重载结束日志。
         * End datapack reload.
         */
        fun endDatapackReload(loadedCount: Int) {
            logger.info(
                "Datapack machine types: loaded={}, live={}, code={}, networkIds={}",
                loadedCount,
                liveById.size,
                codeById.size,
                stringToNetworkId.size,
            )
        }

        fun byId(id: String): MachineType? = liveById[id]?.validOrNull()

        fun byId(id: Short): MachineType? = types.getOrNull(id.toInt())

        fun byRecipeType(recipeType: RecipeType<*>): MachineType? =
            getAll().firstOrNull { it.recipeType === recipeType }

        fun byItem(stack: ItemStack): MachineType? {
            if (stack.isEmpty) return null
            return getAll().firstOrNull { it.accepts(stack) }
        }

        fun acceptsAny(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            return getAll().any { it.accepts(stack) }
        }

        fun getAll(): List<MachineType> = liveById.values.filter { it.valid }

        fun indexById(id: String): Int {
            val list = getAll()
            val idx = list.indexOfFirst { it.id == id }
            return if (idx >= 0) idx else 0
        }

        private fun install(type: MachineType) {
            ensureNetworkId(type)

            if (!type.fromDatapack) {
                val prevCode = codeById.put(type.id, type)
                if (prevCode != null && prevCode !== type) {
                    prevCode.valid = false
                }
            }

            val prevLive = liveById[type.id]
            if (prevLive != null && prevLive !== type) {
                // 数据包覆盖代码，或同 id 替换
                if (type.fromDatapack || !prevLive.fromDatapack) {
                    prevLive.valid = false
                    liveById[type.id] = type
                    type.valid = true
                } else {
                    // 代码类型在数据包仍 live 时构造：代码待命，不抢 live
                    type.valid = false
                    // 若 prev 已失效则接管
                    if (!prevLive.valid) {
                        liveById[type.id] = type
                        type.valid = true
                    }
                }
            } else {
                liveById[type.id] = type
                type.valid = true
            }
        }

        private fun ensureNetworkId(type: MachineType): Short {
            stringToNetworkId[type.id]?.let { return it }
            synchronized(types) {
                val nid = types.size
                require(nid <= Short.MAX_VALUE) { "Too Many MachineTypes" }
                types.add(type)
                nid.toShort()
            }.apply {
                type.networkId = this
                stringToNetworkId[type.id] = this
                return this
            }
        }
    }
}
