package allyouneed.util

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

    companion object {
        private fun <T> load(clazz: Class<T>): T = ServiceLoader.load(clazz).findFirst().orElseThrow {
            IllegalStateException("Failed to load service for ${clazz.name}")
        }.also {
            logger.debug("Loaded {} for service {}", it, clazz)
        }

        fun load(): PlatformHelper = load(PlatformHelper::class.java)
    }
}
