package allyouneed.logic.machine

import allyouneed.util.bigint.BigIngredient
import allyouneed.util.bigint.BigStack
import allyouneed.util.globalId
import appeng.api.stacks.AEItemKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import java.math.BigInteger

/**
 * 绑定到某 [MachineType] 的手动配方（代码或数据包）。
 *
 * - [inputs]：按槽位对齐的 [BigIngredient]（精确 AEKey 或 Ingredient 通配，数量语义同 [BigStack]）
 * - [outputs] / [remainders]：确定的 [BigStack]（不能通配）
 *
 * Manual recipe bound to a [MachineType].
 * Inputs use [BigIngredient] (exact or wildcard); outputs/remainders are concrete [BigStack]s.
 */
data class ManualMachineRecipe(
    val id: ResourceLocation? = null,
    val machineType: MachineType,
    val inputs: List<BigIngredient>,
    val outputs: List<BigStack>,
    /**
     * 可选：合成后各槽残留（与输入槽对齐）；null 表示全空。
     * Optional per-slot remainders aligned to input slots; null → all empty.
     */
    val remainders: List<BigStack?>? = null,
) {
    init {
        require(outputs.isNotEmpty()) { "ManualMachineRecipe requires at least one output" }
        require(machineType.valid) { "ManualMachineRecipe machineType must be valid: ${machineType.id}" }
        // 缓存精确键的全局 id
        for (ing in inputs) {
            ing.key?.globalId
        }
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

    /**
     * 主产物（首个 output）转 ItemStack，供装配室现有 Item 管线使用。
     * Primary output as ItemStack for current assembler item pipeline.
     */
    fun primaryOutputStack(): ItemStack {
        val out = outputs.first()
        val key = out.key as? AEItemKey ?: return ItemStack.EMPTY
        val count = out.valIntSaturate.coerceAtLeast(1)
        return key.toStack(count)
    }

    fun remainderStacks(container: Container): List<ItemStack> {
        val size = container.containerSize
        if (remainders == null) {
            return List(size) { ItemStack.EMPTY }
        }
        return List(size) { i ->
            val big = remainders.getOrNull(i) ?: return@List ItemStack.EMPTY
            val key = big.key as? AEItemKey ?: return@List ItemStack.EMPTY
            val count = big.valIntSaturate
            if (count <= 0) ItemStack.EMPTY else key.toStack(count)
        }
    }
}
