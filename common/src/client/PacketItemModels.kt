package allyouneed.client

import allyouneed.item.packet.AllPackets
import allyouneed.mixin.client.minecraft.ItemPropertiesAccessor
import allyouneed.util.MODID
import net.minecraft.resources.ResourceLocation

object PacketItemModels {
    fun init() {
        ItemPropertiesAccessor.invokeRegister(
            AllPackets.packet.asItem(),
            ResourceLocation(MODID, "type"),
        ) { stack, _, _, _ -> AllPackets.modelIndex(stack) }
    }
}
