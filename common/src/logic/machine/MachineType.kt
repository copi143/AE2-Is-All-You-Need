package allyouneed.logic.machine

import allyouneed.util.logger
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * 分子装配室可执行的配方类别（不是具体机器方块）。
 * 构造时自动注册；数据包重载会使旧实例 [valid]=false。
 *
 * Recipe category for the machine assembler (not a concrete block).
 * Auto-registers on construct; reload invalidates replaced instances.
 *
 * 解析：手动配方 → [recipeSource]。
 */
class MachineType(
    val id: String,
    /**
     * 机器类型的显示名称
     */
    val name: Component,
    /**
     * 显示的图标（一般使用默认机器）
     */
    val icon: ItemStack,
    /**
     * 占用装配室的输入槽位数，为 0 则不允许样板以外的槽位填充方式。
     *
     * How many input slots this category uses.
     */
    val inputSlots: Int = 0,
    /**
     * 占用装配室的输出槽位数，为 0 则随配方调整槽位。
     *
     * How many output slots this category uses.
     */
    val outputSlots: Int = 0,
    val machineMatcher: MachineItemMatcher,
    /**
     * 手动配方未命中时的回退配方源。
     *
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
    @Volatile
    var valid: Boolean = false
        private set

    fun validOrNull(): MachineType? = if (valid) this else null

    /** 运行时 int16；非存档稳定，重载不回收。 */
    var networkId: Short = -1
        private set

    init {
        install(this)
    }

    fun accepts(stack: ItemStack): Boolean = valid && machineMatcher.matches(stack)

    fun resolve(level: Level, container: Container): ItemStack? {
        if (!valid) return null
        ManualMachineRecipes.resolve(this, container)?.let { return it }
        return recipeSource?.resolve(level, container)
    }

    fun remainders(level: Level, container: Container): List<ItemStack> {
        if (!valid) return List(container.containerSize) { ItemStack.EMPTY }
        ManualMachineRecipes.remainders(this, container)?.let { return it }
        return recipeSource?.remainders(level, container)
            ?: List(container.containerSize) { ItemStack.EMPTY }
    }

    companion object {
        /** networkId → 实例（含已失效）；下标即 id。 */
        private val byNetwork = ArrayList<MachineType?>()

        private val codeById = LinkedHashMap<String, MachineType>()
        private val liveById = LinkedHashMap<String, MachineType>()
        private val stringToNetworkId = HashMap<String, Short>()

        fun idOf(recipeType: RecipeType<*>): String =
            BuiltInRegistries.RECIPE_TYPE.getKey(recipeType)?.toString() ?: recipeType.toString()

        fun idOf(rl: ResourceLocation): String = rl.toString()

        /** 数据包重载开始：失效 datapack live，恢复代码类型。 */
        fun beginDatapackReload() {
            for (id in liveById.filter { it.value.fromDatapack }.keys.toList()) {
                liveById.remove(id)?.let { it.valid = false }
                codeById[id]?.let {
                    it.valid = true
                    liveById[id] = it
                }
            }
        }

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

        fun byId(id: Short): MachineType? = byNetwork.getOrNull(id.toInt())?.validOrNull()

        fun byRecipeType(recipeType: RecipeType<*>): MachineType? =
            getAll().firstOrNull { it.recipeType === recipeType }

        fun byItem(stack: ItemStack): MachineType? {
            if (stack.isEmpty) return null
            return getAll().firstOrNull { it.accepts(stack) }
        }

        fun acceptsAny(stack: ItemStack): Boolean =
            !stack.isEmpty && getAll().any { it.accepts(stack) }

        fun getAll(): List<MachineType> = liveById.values.filter { it.valid }

        fun indexById(id: String): Int {
            val list = getAll()
            val idx = list.indexOfFirst { it.id == id }
            return if (idx >= 0) idx else 0
        }

        private fun install(type: MachineType) {
            type.networkId = ensureNetworkId(type)

            if (!type.fromDatapack) {
                codeById.put(type.id, type)?.takeIf { it !== type }?.let { it.valid = false }
            }

            val prev = liveById.put(type.id, type)
            if (prev != null && prev !== type) prev.valid = false
            type.valid = true
        }

        private fun ensureNetworkId(type: MachineType): Short {
            stringToNetworkId[type.id]?.let { existing ->
                type.networkId = existing
                // 更新下标指向最新实例
                val i = existing.toInt()
                if (i in byNetwork.indices) byNetwork[i] = type
                return existing
            }
            synchronized(byNetwork) {
                val nid = byNetwork.size
                require(nid <= Short.MAX_VALUE) { "Too many MachineTypes" }
                byNetwork.add(type)
                val s = nid.toShort()
                type.networkId = s
                stringToNetworkId[type.id] = s
                return s
            }
        }
    }
}
