package allyouneed.client.integration.emi.fold

import dev.emi.emi.EmiPort
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.screen.tooltip.EmiTextTooltipWrapper
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent

/**
 * 已展开组的成员槽：行为与被包装的 [member] 完全一致（配方/用途/作弊/
 * 收藏都按单成员处理），仅多画组背景并在 tooltip 末尾标注所属组。
 *
 * [equals]/[hashCode] 按成员语义实现，与裸成员互相相等，保证历史记录去重、
 * 配方上下文匹配等不受包装影响。
 */
class GroupedIngredient(
    val member: EmiStack,
    val groupKey: String,
    private var amount: Long = member.amount,
    private var chance: Float = member.chance,
) : EmiIngredient {
    /** 组内名，人性化展示用。 */
    val displayName: String get() = EmiFoldGroups.title(groupKey)

    private fun displayed(): EmiStack = if (amount == member.amount && chance == member.chance) member
        else member.copy().setAmount(amount).setChance(chance)

    override fun getEmiStacks(): List<EmiStack> = listOf(displayed())

    override fun isEmpty(): Boolean = member.isEmpty()

    override fun copy(): EmiIngredient = GroupedIngredient(member, groupKey, amount, chance)

    override fun getAmount(): Long = amount

    override fun setAmount(amount: Long): EmiIngredient {
        this.amount = amount
        return this
    }

    override fun getChance(): Float = chance

    override fun setChance(chance: Float): EmiIngredient {
        this.chance = chance
        return this
    }

    override fun equals(other: Any?): Boolean =
        other is EmiIngredient && EmiIngredient.areEqual(this, other)

    override fun hashCode(): Int = member.hashCode()

    override fun toString(): String = "Grouped($groupKey: $member)"

    override fun render(draw: GuiGraphics, x: Int, y: Int, delta: Float, flags: Int) {
        if (member.isEmpty()) return
        if (flags and EmiIngredient.RENDER_ICON != 0) {
            EmiFoldRender.drawGroupFill(draw, x, y, groupKey)
        }
        displayed().render(draw, x, y, delta, flags)
    }

    override fun getTooltip(): List<ClientTooltipComponent> {
        val out = ArrayList<ClientTooltipComponent>(displayed().tooltip)
        out.add(
            EmiTextTooltipWrapper(
                this,
                EmiPort.ordered(
                    EmiPort.translatable("tooltip.ae2isallyouneed.emi_fold.member", displayName),
                ),
            ),
        )
        return out
    }
}
