package allyouneed.client

import net.minecraft.core.Registry
import net.minecraft.world.item.CreativeModeTab

class CreativeTab {
    val subTabs = HashMap<String, CreativeSubTab>()

    fun subTab(name: String) = subTabs.getOrPut(name) { CreativeSubTab(this, name) }

    fun init(registry: Registry<CreativeModeTab>) {
//        val tab: CreativeModeTab =
//        Registry.register(registry, AECreativeTabIds.MAIN, tab)
    }
}
