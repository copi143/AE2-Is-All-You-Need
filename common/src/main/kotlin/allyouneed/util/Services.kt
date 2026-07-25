package allyouneed.util

import java.util.ServiceLoader

object Services {
    val platform = load(PlatformHelper::class.java)

    fun <T> load(clazz: Class<T>): T {
        val loadedService = ServiceLoader.load(clazz).findFirst().orElseThrow {
                IllegalStateException("Failed to load service for ${clazz.name}")
            }
        logger.debug("Loaded {} for service {}", loadedService, clazz)
        return loadedService
    }
}
