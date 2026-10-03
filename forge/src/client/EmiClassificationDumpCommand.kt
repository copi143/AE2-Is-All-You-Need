package allyouneed.client

import allyouneed.Platform
import allyouneed.client.integration.emi.EmiClassificationDump
import allyouneed.util.MODID
import net.minecraft.commands.Commands
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterClientCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = [Dist.CLIENT])
object EmiClassificationDumpCommand {
    @SubscribeEvent
    fun register(event: RegisterClientCommandsEvent) {
        if (!Platform.isModLoaded("emi")) return
        event.dispatcher.register(
            Commands.literal("ae2inya").then(
                Commands.literal("dump-emi-classification").executes { EmiClassificationDump.request() },
            ),
        )
    }
}
