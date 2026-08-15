package allyouneed.util

import net.minecraft.client.KeyMapping
import java.util.*

interface PlatformHelper {
    /**
     * Gets the name of the current platform.
     */
    val name: String

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    fun isModLoaded(modId: String): Boolean

    val isDev: Boolean

    val envName get() = if (isDev) "development" else "production"

    /**
     * Registers a client-side key binding so it shows up in the Controls screen.
     * Must be called on the client before/at the loader's key-binding registration phase.
     */
    fun registerKeyBinding(key: KeyMapping)

    /**
     * Registers a handler that is invoked once per client tick, after the tick has ended.
     */
    fun onClientTick(handler: () -> Unit)

    /**
     * 一单位的 AE2 能源对应目标能源的数值。
     */
    fun energyUnitRatio(id: String): Double {
        return when (id) {
            "ae2" -> 1.0
            "forge" -> 2.0
            "team_reborn_energy" -> 0.5
            "gtceu" -> 0.5
            else -> 1.0
        }
    }

    /**
     * 一单位的 AE2 魔力对应目标魔力的数值。
     */
    fun manaUnitRatio(id: String): Double {
        return when (id) {
            "ae2" -> 1.0
            "botania" -> 1.0
            "bloodmagic" -> 1.0
            "ars_nouveau" -> 1.0
            else -> 1.0
        }
    }

    companion object {
        private fun <T> load(clazz: Class<T>): T = ServiceLoader.load(clazz).findFirst().orElseThrow {
            IllegalStateException("Failed to load service for ${clazz.name}")
        }.also {
            logger.debug("Loaded {} for service {}", it, clazz)
        }

        fun load(): PlatformHelper = load(PlatformHelper::class.java)
    }
}
