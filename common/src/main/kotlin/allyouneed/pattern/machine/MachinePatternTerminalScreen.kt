package allyouneed.pattern.machine

import appeng.client.gui.me.items.PatternEncodingTermScreen
import appeng.client.gui.style.Blitter
import appeng.client.gui.style.ScreenStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

class MachinePatternTerminalScreen(
    menu: MachinePatternTerminalMenu,
    playerInventory: Inventory,
    title: Component,
    style: ScreenStyle,
) : PatternEncodingTermScreen<MachinePatternTerminalMenu>(menu, playerInventory, title, style) {

    init {
        widgets.add("machineSlot", MachineSlotWidget(menu))
    }

    override fun updateBeforeRender() {
        super.updateBeforeRender()
        val machine = menu.selectedMachine
        if (machine != null) {
            setTextContent("machine_slot_label", Component.literal(machine.name))
        }
    }
}

private class MachineSlotWidget(
    private val menu: MachinePatternTerminalMenu,
) : AbstractWidget(0, 0, 18, 18, Component.empty()) {

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        Blitter.texture(MACHINE_SLOT_TEXTURE).dest(x, y).blit(guiGraphics)

        val machine = menu.selectedMachine
        if (machine != null) {
            guiGraphics.renderItem(machine.icon, x + 1, y + 1)
        }

        if (isHovered) {
            val lines = mutableListOf<Component>()
            lines += if (machine != null) {
                Component.literal(machine.name)
            } else {
                Component.translatable("gui.ae2isallyouneed.machine_slot_no_machine")
            }
            lines += Component.translatable("gui.ae2isallyouneed.machine_slot_hint")
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY)
        }
    }

    override fun updateWidgetNarration(narration: NarrationElementOutput) {
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isHovered) {
            menu.cycleMachine()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    companion object {
        private val MACHINE_SLOT_TEXTURE = ResourceLocation("ae2isallyouneed", "textures/guis/machine_slot.png")
    }
}
