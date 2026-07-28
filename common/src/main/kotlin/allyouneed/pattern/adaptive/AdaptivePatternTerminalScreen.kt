package allyouneed.pattern.adaptive

import appeng.client.gui.me.items.PatternEncodingTermScreen
import appeng.client.gui.style.ScreenStyle
import appeng.client.gui.widgets.AETextField
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class AdaptivePatternTerminalScreen(
    menu: AdaptivePatternTerminalMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle
) : PatternEncodingTermScreen<AdaptivePatternTerminalMenu>(menu, playerInventory, title, style) {

    private val probabilityField: AETextField
    private val timeoutField: AETextField

    init {
        probabilityField = widgets.addTextField("adaptiveProbability")
        probabilityField.setMaxLength(8)
        probabilityField.setMessage(Component.translatable("gui.ae2isallyouneed.adaptive_probability"))
        probabilityField.setValue(formatProbability(menu.probability))
        probabilityField.setResponder { value ->
            parseDouble(value)?.let { menu.setProbability(it) }
        }

        timeoutField = widgets.addTextField("adaptiveTimeout")
        timeoutField.setMaxLength(5)
        timeoutField.setMessage(Component.translatable("gui.ae2isallyouneed.adaptive_timeout"))
        timeoutField.setValue(menu.timeout.toString())
        timeoutField.setResponder { value ->
            parseInt(value)?.let { menu.setTimeout(it) }
        }
    }

    override fun updateBeforeRender() {
        super.updateBeforeRender()
        if (!probabilityField.isFocused) {
            val current = formatProbability(menu.probability)
            if (current != probabilityField.value) {
                probabilityField.value = current
            }
        }
        if (!timeoutField.isFocused) {
            val current = menu.timeout.toString()
            if (current != timeoutField.value) {
                timeoutField.value = current
            }
        }
    }

    override fun render(guiGraphics: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        probabilityField.render(guiGraphics, mouseX, mouseY, partialTick)
        timeoutField.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    companion object {
        private fun formatProbability(p: Double): String = "%.4f".format(p)

        private fun parseDouble(s: String): Double? {
            return try {
                val v = s.trim().toDouble()
                if (v in 0.01..1.0) v else null
            } catch (_: NumberFormatException) {
                null
            }
        }

        private fun parseInt(s: String): Int? {
            return try {
                val v = s.trim().toInt()
                if (v in 1..3600) v else null
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
