package allyouneed.forge.init

import allyouneed.CommonObject
import allyouneed.util.MODID
import appeng.api.features.P2PTunnelAttunement
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
object ForgeCommonEvents {
    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            CommonObject.commonSetup()
            P2PTunnelAttunement.registerAttunementTag(ForgeItems.ENTITY_P2P_TUNNEL.get())
        }
    }
}
