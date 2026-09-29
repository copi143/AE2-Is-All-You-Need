package allyouneed.item.packet

import allyouneed.util.MODID
import allyouneed.util.MetricFormat
import allyouneed.util.mcText
import allyouneed.util.mcTranslate
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class PacketItem : Item(Properties().stacksTo(64)) {

    override fun getName(stack: ItemStack): Component {
        val key = AllPackets.toAEKey(stack) ?: return super.getName(stack)
        return "item.$MODID.packet.typed".mcTranslate(key.displayName)
    }

    override fun appendHoverText(
        stack: ItemStack, level: Level?, lines: MutableList<Component>, advanced: TooltipFlag,
    ) {
        val key = AllPackets.toAEKey(stack) ?: return
        val amount = AllPackets.getResourceAmount(stack)

        lines.add(key.type.description)
        lines.add(MetricFormat.siFormat(amount).mcText)
    }
}
