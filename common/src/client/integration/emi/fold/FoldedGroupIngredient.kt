package allyouneed.client.integration.emi.fold

import dev.emi.emi.EmiPort
import dev.emi.emi.api.stack.EmiIngredient
import dev.emi.emi.api.stack.EmiStack
import dev.emi.emi.api.stack.ListEmiIngredient
import allyouneed.client.integration.emi.fold.model.FoldTree
import dev.emi.emi.screen.tooltip.EmiTextTooltipWrapper
import dev.emi.emi.screen.tooltip.IngredientTooltipComponent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent

/**
 * EMI 侧边栏用的可折叠同类组。
 *
 * 普通点击使用 EMI 的多选材料界面，`Alt+点击` 内联展开小组或钻入大组。
 * 图标位轮播成员并叠加 `+N` 角标。
 */
class FoldedGroupIngredient(
    val groupKey: String,
    members: List<EmiStack>,
    private var amount: Long = 1,
    private var chance: Float = 1f,
    private val resolveFromIndex: Boolean = false,
    private val savedName: String? = null,
) : ListEmiIngredient(members, amount) {
    private val fallbackMembers = members
    val members: List<EmiStack> get() = if (resolveFromIndex) EmiFoldGroups.resolve(groupKey) ?: fallbackMembers else fallbackMembers
    companion object {
        /** tooltip 里最多展示的成员图标数，超出只显示数量。 */
        const val TOOLTIP_ICON_CAP = 24
    }

    val size: Int get() = members.size

    private fun current(): EmiStack =
        members[(System.currentTimeMillis() / 1000 % members.size).toInt()]

    /** 组内名，人性化展示用。 */
    val displayName: String get() = EmiFoldGroups.title(groupKey, savedName)

    override fun getEmiStacks(): List<EmiStack> = members
    override fun getIngredients(): List<EmiIngredient> = members

    override fun isEmpty(): Boolean = members.isEmpty()

    override fun copy(): EmiIngredient = FoldedGroupIngredient(groupKey, members, amount, chance, resolveFromIndex, savedName)

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
        other is ListEmiIngredient && members == other.emiStacks

    override fun hashCode(): Int = members.hashCode()

    override fun toString(): String = "FoldedGroup($groupKey x$size)"

    override fun render(draw: GuiGraphics, x: Int, y: Int, delta: Float, flags: Int) {
        if (members.isEmpty()) return
        val cur = current()
        if (flags and EmiIngredient.RENDER_ICON != 0) {
            // 组底色先于图标绘制（同 z，后画者胜）。边框由 EmiFoldOutline
            // 在侧边栏收尾统一描合并外轮廓，此处不画。
            EmiFoldRender.drawGroupFill(draw, x, y, groupKey)
            cur.render(draw, x, y, delta, flags and EmiIngredient.RENDER_AMOUNT.inv())
            // 右下角数量角标（相对槽原点，见 EmiFoldRender 的坐标说明）。
            val font = Minecraft.getInstance().font
            val text = if (size >= 1000) "+${size / 1000}k" else "+$size"
            val w = font.width(text)
            val scale = minOf(1f, 14f / w.coerceAtLeast(1))
            val sx = x - 1
            val sy = y - 1
            draw.pose().pushPose()
            draw.pose().translate(0f, 0f, 200f)
            draw.fill(sx + 16 - (w * scale).toInt(), sy + 9, sx + 18, sy + 18, 0xAA000000.toInt())
            draw.pose().translate(sx + 17f - w * scale, sy + 17f - font.lineHeight * scale, 0f)
            draw.pose().scale(scale, scale, 1f)
            draw.drawString(font, text, 0, 0, 0xFFFF55, true)
            draw.pose().popPose()
        }
        if (flags and EmiIngredient.RENDER_AMOUNT != 0) {
            cur.copy().setAmount(amount).render(draw, x, y, delta, EmiIngredient.RENDER_AMOUNT)
        }
        // 有意不画 RENDER_INGREDIENT 默认角标：角标已由 +N 代替。
    }

    override fun getTooltip(): List<ClientTooltipComponent> {
        if (members.isEmpty()) return listOf()
        val out = ArrayList<ClientTooltipComponent>()
        out.add(
            EmiTextTooltipWrapper(
                this,
                EmiPort.ordered(
                    EmiPort.translatable("tooltip.ae2isallyouneed.emi_fold.group", displayName, size),
                ),
            ),
        )
        EmiFoldGroups.pathTitle(groupKey)?.takeIf { it != displayName }?.let { path ->
            out.add(EmiTextTooltipWrapper(this, EmiPort.ordered(EmiPort.literal(path))))
        }
        val shown = members.take(TOOLTIP_ICON_CAP)
        out.add(IngredientTooltipComponent(shown))
        if (size > shown.size) {
            out.add(
                EmiTextTooltipWrapper(
                    this,
                    EmiPort.ordered(
                        EmiPort.translatable("tooltip.ae2isallyouneed.emi_fold.more", size - shown.size),
                    ),
                ),
            )
        }
        val cur = current()
        out.add(EmiTextTooltipWrapper(this, EmiPort.ordered(cur.name)))
        out.add(
            EmiTextTooltipWrapper(
                this,
                EmiPort.ordered(
                    EmiPort.translatable(if (size < FoldTree.SMALL_GROUP_LIMIT)
                        "tooltip.ae2isallyouneed.emi_fold.hint_inline_expand" else "tooltip.ae2isallyouneed.emi_fold.hint_open"),
                ),
            ),
        )
        return out
    }
}
