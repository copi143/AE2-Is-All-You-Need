package allyouneed.integration.emi

import dev.emi.emi.api.EmiEntrypoint
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.widget.Bounds
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen

@EmiEntrypoint
class MyEmiPlugin : EmiPlugin {
    companion object {
        private const val IMAGE_WIDTH = 195
        private const val IMAGE_HEIGHT = 136
        private const val SIDEBAR_WIDTH = 80
        private const val SIDEBAR_PADDING = 2
    }

    override fun register(registry: EmiRegistry) {
        registry.addExclusionArea(CreativeModeInventoryScreen::class.java) { screen, consumer ->
            val left = (screen.width - IMAGE_WIDTH) / 2
            val top = (screen.height - IMAGE_HEIGHT) / 2
            val sidebarX = left - SIDEBAR_WIDTH - SIDEBAR_PADDING
            val sidebarY = top
            consumer.accept(Bounds(sidebarX, sidebarY, SIDEBAR_WIDTH, IMAGE_HEIGHT))
        }
    }
}
