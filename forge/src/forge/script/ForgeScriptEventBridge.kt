package allyouneed.forge.script

import allyouneed.util.MODID
import kaptor.runtime.ScriptEventBus
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
object ForgeScriptEventBridge {

    @SubscribeEvent
    fun onPlayerInteractRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        dispatchWithEventMap("PlayerInteractEvent.RightClickBlock", event)
    }

    @SubscribeEvent
    fun onPlayerInteractLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        dispatchWithEventMap("PlayerInteractEvent.LeftClickBlock", event)
    }

    @SubscribeEvent
    fun onPlayerInteractRightClickItem(event: PlayerInteractEvent.RightClickItem) {
        dispatchWithEventMap("PlayerInteractEvent.RightClickItem", event)
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        dispatchWithEventMap("PlayerEvent.PlayerLoggedIn", event)
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        dispatchWithEventMap("PlayerEvent.PlayerLoggedOut", event)
    }

    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerEvent.PlayerRespawnEvent) {
        dispatchWithEventMap("PlayerEvent.PlayerRespawn", event)
    }

    @SubscribeEvent
    fun onPlayerClone(event: PlayerEvent.Clone) {
        dispatchWithEventMap("PlayerEvent.Clone", event)
    }

    @SubscribeEvent
    fun onPlayerChangedDimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        dispatchWithEventMap("PlayerEvent.PlayerChangedDimension", event)
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            dispatchWithEventMap("ServerTickEvent", event)
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            dispatchWithEventMap("ClientTickEvent", event)
        }
    }

    private fun dispatchWithEventMap(eventType: String, event: Any) {
        val eventMap = HashMap<String, Any?>()
        eventMap["eventType"] = eventType

        when (event) {
            is PlayerInteractEvent.RightClickBlock -> {
                val player = event.entity
                eventMap["player"] = player
                eventMap["blockPos"] = event.pos
                eventMap["level"] = player?.level()
                eventMap["hand"] = event.hand
                eventMap["itemStack"] = event.itemStack
                eventMap["face"] = event.face
            }

            is PlayerInteractEvent.LeftClickBlock -> {
                val player = event.entity
                eventMap["player"] = player
                eventMap["blockPos"] = event.pos
                eventMap["level"] = player?.level()
                eventMap["hand"] = event.hand
            }

            is PlayerInteractEvent.RightClickItem -> {
                val player = event.entity
                eventMap["player"] = player
                eventMap["level"] = player?.level()
                eventMap["hand"] = event.hand
                eventMap["itemStack"] = event.itemStack
            }

            is PlayerEvent -> {
                eventMap["player"] = event.entity
            }

            is TickEvent -> {
                eventMap["phase"] = event.phase.name
            }
        }

        eventMap["setCanceled"] = { cancel: Boolean ->
            if (event is Event && event.isCancelable) {
                event.isCanceled = cancel
            }
        }

        eventMap["sendMessage"] = { msg: Any? ->
            val player = eventMap["player"] as? Player
            player?.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(msg?.toString() ?: "null")
            )
        }

        ScriptEventBus.dispatchEvent(eventType, eventMap)
    }
}
