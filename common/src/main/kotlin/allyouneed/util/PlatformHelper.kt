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

    companion object {
        private fun <T> load(clazz: Class<T>): T = ServiceLoader.load(clazz).findFirst().orElseThrow {
            IllegalStateException("Failed to load service for ${clazz.name}")
        }.also {
            logger.debug("Loaded {} for service {}", it, clazz)
        }

        fun load(): PlatformHelper = load(PlatformHelper::class.java)
    }
}
