package allyouneed

import allyouneed.util.PlatformHelper

object Platform : PlatformHelper {
    private val helper = PlatformHelper.load()
    override val name: String = helper.name
    override fun isModLoaded(modId: String): Boolean = helper.isModLoaded(modId)
    override val isDev: Boolean = helper.isDev
}
