package allyouneed.platform

import allyouneed.platform.services.PlatformHelper
import allyouneed.util.logger
import java.util.*

object Services {
    val PLATFORM = load(PlatformHelper::class.java)

    fun <T> load(clazz: Class<T>): T {
        val loadedService = ServiceLoader.load(clazz).findFirst().orElseThrow {
                IllegalStateException("Failed to load service for ${clazz.name}")
            }
        logger.debug("Loaded {} for service {}", loadedService, clazz)
        return loadedService
    }
}
