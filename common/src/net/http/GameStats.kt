package allyouneed.net.http

import net.minecraft.server.MinecraftServer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min
import kotlin.math.round

object GameStats {
    private val extras = CopyOnWriteArrayList<Pair<String, (MinecraftServer) -> Any?>>()

    fun register(key: String, collector: (MinecraftServer) -> Any?) {
        extras.removeAll { it.first == key }
        extras += key to collector
    }

    fun snapshot(server: MinecraftServer): Map<String, Any?> {
        val mspt = server.averageTickTime.toDouble()
        val tps = if (mspt <= 0.0) 20.0 else min(20.0, 1000.0 / mspt)
        val names = if (server.hidesOnlinePlayers()) emptyList() else server.playerNames.toList()
        val map = linkedMapOf<String, Any?>(
            "players" to linkedMapOf(
                "online" to server.playerCount,
                "max" to server.maxPlayers,
                "names" to names,
            ),
            "tick" to linkedMapOf(
                "mspt" to mspt.places(2),
                "tps" to tps.places(2),
                "count" to server.tickCount,
            ),
            "server" to linkedMapOf(
                "motd" to server.motd,
                "dedicated" to server.isDedicatedServer,
                "version" to server.serverVersion,
                "port" to server.port,
            ),
            "worlds" to server.allLevels.map { level ->
                linkedMapOf(
                    "id" to level.dimension().location().toString(),
                    "players" to level.players().size,
                    "dayTime" to level.dayTime,
                )
            },
        )
        for ((key, collector) in extras) {
            map[key] = collector(server)
        }
        return map
    }

    private fun Double.places(n: Int): Double {
        var f = 1.0
        repeat(n) { f *= 10.0 }
        return round(this * f) / f
    }
}
