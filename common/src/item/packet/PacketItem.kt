package allyouneed.item.packet

import allyouneed.util.MODID
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

open class PacketItem : Item(Properties().stacksTo(64)) {

    override fun appendHoverText(
        stack: ItemStack,
        level: Level?,
        lines: MutableList<Component>,
        advanced: TooltipFlag,
    ) {
        val tag = stack.tag ?: return
        if (!tag.contains(AllPackets.TAG_TYPE)) return

        val type = tag.getString(AllPackets.TAG_TYPE)
        val amount = tag.getLong(AllPackets.TAG_AMOUNT)

        val typeName = when (type) {
            AllPackets.TYPE_ENERGY -> Component.translatable("packet.$MODID.type.energy")
            AllPackets.TYPE_MANA -> Component.translatable("packet.$MODID.type.mana")
            AllPackets.TYPE_FLUID -> Component.translatable("packet.$MODID.type.fluid")
            AllPackets.TYPE_ITEM -> Component.translatable("packet.$MODID.type.item")
            AllPackets.TYPE_HP -> Component.translatable("packet.$MODID.type.hp")
            AllPackets.TYPE_STA -> Component.translatable("packet.$MODID.type.sta")
            AllPackets.TYPE_XP -> Component.translatable("packet.$MODID.type.xp")
            else -> Component.literal(type)
        }

        // Sub-type info
        val subType = when (type) {
            AllPackets.TYPE_ENERGY -> tag.getString(AllPackets.TAG_METRIC)
            AllPackets.TYPE_MANA -> tag.getString(AllPackets.TAG_METRIC)
            AllPackets.TYPE_FLUID -> tag.getString(AllPackets.TAG_FLUID)
            AllPackets.TYPE_ITEM -> tag.getString(AllPackets.TAG_ITEM)
            AllPackets.TYPE_HP, AllPackets.TYPE_STA, AllPackets.TYPE_XP -> {
                val lvl = tag.getInt(AllPackets.TAG_LEVEL)
                if (lvl > 0) "Lv.$lvl" else null
            }
            else -> null
        }

        lines.add(
            Component.literal("§7")
                .append(typeName)
                .append(if (subType != null) Component.literal(" - $subType") else Component.empty())
        )
        lines.add(
            Component.literal("§7${formatAmount(amount)}")
        )
    }

    private fun formatAmount(amount: Long): String {
        return when {
            amount >= 1_000_000_000 -> String.format("%.1fG", amount / 1_000_000_000.0)
            amount >= 1_000_000 -> String.format("%.1fM", amount / 1_000_000.0)
            amount >= 1_000 -> String.format("%.1fK", amount / 1_000.0)
            else -> amount.toString()
        }
    }
}
