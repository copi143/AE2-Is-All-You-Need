package allyouneed.client.integration.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.gui.handlers.IGuiContainerHandler
import mezz.jei.api.registration.IGuiHandlerRegistration
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.renderer.Rect2i
import net.minecraft.resources.ResourceLocation

@JeiPlugin
class MyJeiPlugin : IModPlugin {

    companion object {
        private const val IMAGE_WIDTH = 195
        private const val IMAGE_HEIGHT = 136
        private const val SIDEBAR_WIDTH = 80
        private const val SIDEBAR_PADDING = 2
    }

    override fun getPluginUid(): ResourceLocation {
        return ResourceLocation("allyouneed", "jei_plugin")
    }

    override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
        registration.addGuiContainerHandler(
            CreativeModeInventoryScreen::class.java,
            object : IGuiContainerHandler<CreativeModeInventoryScreen> {
                override fun getGuiExtraAreas(screen: CreativeModeInventoryScreen): List<Rect2i> {
                    val left = (screen.width - IMAGE_WIDTH) / 2
                    val top = (screen.height - IMAGE_HEIGHT) / 2
                    val sidebarX = left - SIDEBAR_WIDTH - SIDEBAR_PADDING
                    val sidebarY = top

                    return listOf(
                        Rect2i(sidebarX, sidebarY, SIDEBAR_WIDTH, IMAGE_HEIGHT)
                    )
                }
            }
        )
    }
}
