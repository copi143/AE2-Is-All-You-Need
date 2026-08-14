package allyouneed.logic.machine

import allyouneed.util.bigint.BigIngredient
import allyouneed.util.bigint.BigStack
import allyouneed.util.globalId
import allyouneed.util.logger
import appeng.api.stacks.AEItemKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

/**
 * 绑定到某 [MachineType] 的手动配方。
 * 输入 [BigIngredient]（可通配），输出/残留 [BigStack]（必须具体）。
 */
data class ManualMachineRecipe(
    val id: ResourceLocation? = null,
    val machineType: MachineType,
    val inputs: List<BigIngredient>,
    val outputs: List<BigStack>,
    val remainders: List<BigStack?>? = null,
) {
    init {
        require(outputs.isNotEmpty()) { "requires at least one output" }
        require(machineType.valid) { "machineType must be valid: ${machineType.id}" }
        inputs.forEach { it.key?.globalId }
        outputs.forEach { it.key.globalId }
        remainders?.forEach { it?.key?.globalId }
    }

    fun matches(container: Container): Boolean {
        if (!machineType.valid) return false
        val slots = inputs.size.coerceAtMost(container.containerSize)
        for (i in 0 until slots) {
            if (!inputs[i].test(container.getItem(i))) return false
        }
        for (i in slots until container.containerSize) {
            if (!container.getItem(i).isEmpty) return false
        }
        return true
    }

    fun primaryOutputStack(): ItemStack {
        val out = outputs.first()
        val key = out.key as? AEItemKey ?: return ItemStack.EMPTY
        return key.toStack(out.valIntSaturate.coerceAtLeast(1))
    }

    fun remainderStacks(containerSize: Int): List<ItemStack> {
        if (remainders == null) return List(containerSize) { ItemStack.EMPTY }
        return List(containerSize) { i ->
            val big = remainders.getOrNull(i) ?: return@List ItemStack.EMPTY
            val key = big.key as? AEItemKey ?: return@List ItemStack.EMPTY
            val n = big.valIntSaturate
            if (n <= 0) ItemStack.EMPTY else key.toStack(n)
        }
    }
}

/**
 * 手动配方索引（按 [MachineType.id] 字符串，重载安全）。
 */
object ManualMachineRecipes {
    @Volatile
    private var byTypeId: Map<String, List<ManualMachineRecipe>> = emptyMap()

    fun forType(machineType: MachineType): List<ManualMachineRecipe> {
        if (!machineType.valid) return emptyList()
        return byTypeId[machineType.id].orEmpty()
    }

    fun match(machineType: MachineType, container: Container): ManualMachineRecipe? =
        forType(machineType).firstOrNull { it.matches(container) }

    fun resolve(machineType: MachineType, container: Container): ItemStack? =
        match(machineType, container)?.primaryOutputStack()

    fun remainders(machineType: MachineType, container: Container): List<ItemStack>? =
        match(machineType, container)?.remainderStacks(container.containerSize)

    fun replaceAll(recipes: Collection<ManualMachineRecipe>) {
        byTypeId = recipes.groupBy { it.machineType.id }
        logger.info("Loaded {} manual machine recipe(s) across {} type(s)", recipes.size, byTypeId.size)
    }

    fun clear() {
        byTypeId = emptyMap()
    }
}
