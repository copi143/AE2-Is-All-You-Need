package allyouneed

import allyouneed.util.PlatformHelper
import net.minecraft.client.KeyMapping

object Platform : PlatformHelper {
    private val helper = PlatformHelper.load()
    override val name: String = helper.name
    override fun isModLoaded(modId: String): Boolean = helper.isModLoaded(modId)
    override val isDev: Boolean = helper.isDev

    override fun registerKeyBinding(key: KeyMapping) = helper.registerKeyBinding(key)
    override fun onClientTick(handler: () -> Unit) = helper.onClientTick(handler)
}
