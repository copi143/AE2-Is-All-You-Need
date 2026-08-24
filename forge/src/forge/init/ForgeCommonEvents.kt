package allyouneed.forge.init

import allyouneed.Main
import allyouneed.Platform
import allyouneed.forge.botania.BotaniaManaCompat
import allyouneed.util.MODID
import appeng.api.features.P2PTunnelAttunement
import appeng.api.ids.AECreativeTabIds
import appeng.core.definitions.AEParts
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
object ForgeCommonEvents {
    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            Main.commonSetup()
            // Optional Botania integration: mana import/export bus strategies.
            if (Platform.isModLoaded("botania")) {
                BotaniaManaCompat.register()
            }
            P2PTunnelAttunement.registerAttunementTag(ForgeItems.ENTITY_P2P_TUNNEL.get())
        }
    }

    @SubscribeEvent
    fun onCreativeTab(event: BuildCreativeModeTabContentsEvent) {
        if (event.tabKey != AECreativeTabIds.MAIN) return
        event.entries.remove(AEParts.PATTERN_ENCODING_TERMINAL.stack())
    }
}
