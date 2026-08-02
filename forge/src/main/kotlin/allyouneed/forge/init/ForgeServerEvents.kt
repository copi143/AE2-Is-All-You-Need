package allyouneed.forge.init

import allyouneed.cell.dimensional.DimensionalCellStore
import allyouneed.multiblock.MultiblockEditor
import allyouneed.multiblock.MultiblockPatterns
import allyouneed.util.MODID
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.event.server.ServerStartingEvent
import net.minecraftforge.event.server.ServerStoppedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ForgeServerEvents {
    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        DimensionalCellStore.attach(event.server)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        DimensionalCellStore.detach()
    }

    @SubscribeEvent
    fun onAddReloadListener(event: AddReloadListenerEvent) {
        event.addListener(ResourceManagerReloadListener { manager ->
            MultiblockPatterns.reload(manager)
        })
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        MultiblockEditor.registerCommands(event.dispatcher)
    }

    @SubscribeEvent
    fun onTravelToDimension(event: EntityTravelToDimensionEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (MultiblockEditor.isEditorDimension(event.dimension) && !MultiblockEditor.hasSession(player)) {
            event.setCanceled(true)
            player.sendSystemMessage(Component.literal("编辑器维度没有进行中的会话，拒绝传送"))
        }
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            MultiblockEditor.tick(event.server)
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        (event.entity as? ServerPlayer)?.let { MultiblockEditor.clearSession(it) }
    }

    @SubscribeEvent
    fun onServerStarting(event: ServerStartingEvent) {
        MultiblockEditor.onServerStarting(event.server)
    }

    @SubscribeEvent
    fun onServerStopped(event: ServerStoppedEvent) {
        MultiblockEditor.onServerStopped(event.server)
    }
}
