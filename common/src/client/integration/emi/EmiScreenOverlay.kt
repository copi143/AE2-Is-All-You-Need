package allyouneed.client.integration.emi

import allyouneed.Platform
import net.minecraft.client.gui.GuiGraphics

object EmiScreenOverlay {
    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (!Platform.isModLoaded("emi")) return
        runCatching {
            val ctx = Class.forName("dev.emi.emi.runtime.EmiDrawContext")
                .getMethod("wrap", GuiGraphics::class.java)
                .invoke(null, graphics)
            runCatching {
                Class.forName("dev.emi.emi.EmiPort").getMethod("setPositionTexShader").invoke(null)
            }
            Class.forName("dev.emi.emi.screen.EmiScreenManager").methods
                .first { it.name == "render" && it.parameterCount == 4 }
                .invoke(null, ctx, mouseX, mouseY, partialTick)
        }
    }
}
